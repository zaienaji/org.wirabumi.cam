package org.wirabumi.cam.process;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.util.ArgumentException;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBErrorBuilder;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.financialmgmt.assetmgmt.Amortization;
import org.openbravo.model.financialmgmt.assetmgmt.AmortizationLine;
import org.openbravo.model.financialmgmt.assetmgmt.Asset;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;
import org.openbravo.service.db.DalConnectionProvider;
import org.wirabumi.gen.oez.contract.Contract;

public class AssetAmortizationProcess extends DalBaseProcess {
  private final int januaryMonthNumber = 0;
  
  @Override
  protected void doExecute(ProcessBundle bundle) throws Exception {

    final Asset asset = getAsset(bundle);
    Contract.require(asset!=null, "can not find asset");
    
    if (!asset.isDepreciate()){
    	bundle.setResult(OBErrorBuilder.buildMessage(null, "Warning", "asset is not set to be depreciated. No amortization generated for this asset."));
    }
    
    AmortizationMethod amortizationMethod = gerAmortizationMethod(asset);
    switch (amortizationMethod) {
      case Linear:
        runCoreModuleAssetAmortizationGenerationProcess(bundle);
        break;
        
      case DoubleDeclining:
        handleDoubleDecliningAmortizatio(asset);
        break;
    }
    
    bundle.setResult(getObError(ResultType.Success, " Amortization(s) created successfully."));

  }

  private AmortizationMethod gerAmortizationMethod(Asset asset) {

    if (isAssetDepreciationLinear(asset))
      return AmortizationMethod.Linear;

    return AmortizationMethod.DoubleDeclining;

  }

  private void handleDoubleDecliningAmortizatio(Asset asset) {
    Result validation = isAssetEligibleForDepreciation(asset);
    Contract.require(!validation.isError(), validation.getErrorMessage());
    
    removePendingAmortizationLine(asset);
    OBDal.getInstance().refresh(asset);
    
    Map<Date, Amortization> unProcessedAmortization = getUnprocessedAmortizationHeader(asset.getOrganization());
    
    BigDecimal annualDepreciationPercent = getAnnualDepreciationPercent(asset);
    BigDecimal pendingAmortization = asset.getAssetValue();
    
    Calendar amortizationCalendar = Calendar.getInstance();
    amortizationCalendar.setTime(asset.getDepreciationStartDate());
    
    int useLifeYear = asset.getUsableLifeMonths().intValue()/12;
    for (int yearSeq=1; yearSeq<=useLifeYear; yearSeq++){
      BigDecimal monthlyAmortizationTarif = getAmortizationTarif(pendingAmortization, annualDepreciationPercent, amortizationCalendar);
      BigDecimal percentage = asset.getAssetValue().divide(monthlyAmortizationTarif, 2, RoundingMode.HALF_DOWN);
      
      do{
        Date amortizationStartDate = amortizationCalendar.getTime();
        Amortization amortization = getAmortizationHeader(unProcessedAmortization, amortizationStartDate, asset);
        AmortizationLine amortizationLine = createAmortizationLine(asset, amortization, monthlyAmortizationTarif, percentage);
        
        asset.getFinancialMgmtAmortizationLineList().add(amortizationLine);
        amortization.getFinancialMgmtAmortizationLineList().add(amortizationLine);
        OBDal.getInstance().save(amortizationLine);
        OBDal.getInstance().save(asset);
        OBDal.getInstance().save(amortization);
        
        amortizationCalendar.add(Calendar.MONTH, 1);
        pendingAmortization = pendingAmortization.subtract(amortizationLine.getAmortizationAmount());
      }while(isYearChanged(amortizationCalendar));
    }
    
    OBDal.getInstance().commitAndClose();
  }

  private BigDecimal getAmortizationTarif(BigDecimal assetValue,
      BigDecimal annualDepreciationPercent, Calendar amortizationCalendar) {
    BigDecimal yearlyAmortizationTarif = assetValue
        .divide(annualDepreciationPercent, 2, RoundingMode.HALF_DOWN);
    
    BigDecimal monthlyAmortizationTarif = getAmortizationTarif(assetValue, annualDepreciationPercent, amortizationCalendar); 
        yearlyAmortizationTarif
        .divide(new BigDecimal(12-amortizationCalendar.get(Calendar.MONTH)), 2, RoundingMode.HALF_DOWN);
        
    return monthlyAmortizationTarif;
  }

  private Amortization getAmortizationHeader(Map<Date, Amortization> unProcessedAmortization, Date amortizationStartDate, Asset asset) {
    if (unProcessedAmortization.containsKey(amortizationStartDate)) {
      return unProcessedAmortization.get(amortizationStartDate);
    } else {
      Amortization result = createAmortizationHeader(asset.getOrganization(), amortizationStartDate);
      unProcessedAmortization.put(amortizationStartDate, result);
      
      return result;
    }
  }

  private boolean isYearChanged(Calendar amortizationCalendar) {
    return amortizationCalendar.get(Calendar.MONTH)==januaryMonthNumber;
  }

  private BigDecimal getAnnualDepreciationPercent(Asset asset) {
    return asset
        .getAnnualDepreciation()
        .divide(new BigDecimal(100), 2, RoundingMode.HALF_DOWN);
  }

  private AmortizationLine createAmortizationLine(Asset asset, Amortization header,
      BigDecimal amount, BigDecimal amortizationPercentage) {
    
    AmortizationLine result = OBProvider.getInstance().get(AmortizationLine.class);
    result.setAmortization(header);
    result.setAsset(asset);
    result.setCostcenter(asset.getCamCostcenter());
    result.setAmortizationAmount(amount);
    
    long lineNo = header.getFinancialMgmtAmortizationLineList().size()+1;
    result.setLineNo(lineNo);
    
    long assetSeqNo = asset.getFinancialMgmtAmortizationLineList().size()+1;
    result.setSEQNoAsset(assetSeqNo);
    
    result.setAmortizationPercentage(amortizationPercentage);
    result.setAmortizationAmount(amount);
    result.setCurrency(asset.getCurrency());
    
    return result;
  }

  private Amortization createAmortizationHeader(Organization organization,
      Date amortizationStartDate) {
    Date endDate = getEndOfMonth(amortizationStartDate);
    
    Amortization result = OBProvider.getInstance().get(Amortization.class);
    result.setOrganization(organization);
    result.setStartingDate(amortizationStartDate);
    result.setEndingDate(endDate);
    result.setAccountingDate(endDate);
    
    result.setName(amortizationStartDate.toString());
    
    return result;
  }

  private Date getEndOfMonth(Date date) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTime(date);
    calendar.add(Calendar.MONTH, 1);
    calendar.set(Calendar.DAY_OF_MONTH, 1);
    calendar.add(Calendar.DAY_OF_MONTH, -1);
    return calendar.getTime();
  }

  private Map<Date, Amortization> getUnprocessedAmortizationHeader(Organization organization) {
    OBCriteria<Amortization> criteria = OBDal.getInstance().createCriteria(Amortization.class);
    criteria.add(Restrictions.eq(Amortization.PROPERTY_ORGANIZATION, organization));
    criteria.add(Restrictions.eq(Amortization.PROPERTY_PROCESSED, false));
    
    Map<Date, Amortization> result = new HashMap<Date, Amortization>();
    criteria.list().stream().forEach(amortization -> {
      result.put(amortization.getAccountingDate(), amortization);
    });
    
    return result;
  }

  private boolean isAssetDepreciationLinear(Asset asset) {

    DepreciationMethod method = DepreciationMethod.valueOf(asset.getDepreciationType());
    return method == DepreciationMethod.LI;

  }

  private void runCoreModuleAssetAmortizationGenerationProcess(ProcessBundle bundle)
      throws Exception {
    AssetLinearDepreciationMethodProcess existingclass = new AssetLinearDepreciationMethodProcess();
    existingclass.doExecute(bundle);
  }

  private OBError getObError(ResultType resultType, String message) {
    OBError result = new OBError();

    switch (resultType) {
      case Error:
        result.setTitle("Error");
        result.setType("Error");
        break;

      case Warning:
        result.setTitle("Warning");
        result.setType("Warning");
        break;

      case Success:
        result.setTitle("Success");
        result.setType("Success");
        break;

      default:
        throw new ArgumentException("unsupported result type: " + resultType);
    }

    result.setMessage(message);

    return result;
  }

  private Asset getAsset(ProcessBundle bundle) {

    final String assetId = (String) bundle.getParams().get("A_Asset_ID");
    if (StringUtils.isBlank(assetId)) {
      return null;
    }

    return OBDal.getInstance().get(Asset.class, assetId);
  }

  private Result isAssetEligibleForDepreciation(Asset asset) {

    Long uselifemonth = asset.getUsableLifeMonths() != null ? asset.getUsableLifeMonths() : 0;

    DepreciationPeriod period = DepreciationPeriod.valueOf(asset.getAmortize());

    return RuleBuilder
        .Create(asset.getDepreciationStartDate() != null, "depreciation date is empty.")
        .and(asset.getCurrency() != null, "currency date is empty.")
        .and(asset.isDepreciate(), "asset is not deprecited, no amortization created")
        .and(ifDepreciationTypeIsDoubleDecliningThenValidate(asset),
            "deprecication type is double declininig, but annual depreication percentage is NOT a positive number or use life month is not match with given annual depreciation percentage.")
        .and(ifCalculcationTypeIsPercentageThenValidate(asset),
            "calculation type is percentage, but annual depreication percentage is NOT a positive number.")
        .and(period == DepreciationPeriod.MO,
            "depreciation period is not monthly. now only monthly depreciation are supported.")
        .and(uselifemonth > 0,
            "use life month must be positive integer number.")
        .evaluate();
  }

  private boolean ifCalculcationTypeIsPercentageThenValidate(Asset asset) {

    DepreciationCalcType depreciationtype = DepreciationCalcType.valueOf(asset.getCalculateType());

    if (depreciationtype != DepreciationCalcType.PE) {
      return true;
    }

    BigDecimal annualdepreciationpercentage = asset.getAnnualDepreciation();

    return ObjectUtils.compare(annualdepreciationpercentage, BigDecimal.ZERO) >= 0;
  }

  private boolean ifDepreciationTypeIsDoubleDecliningThenValidate(Asset asset) {
    if (!isAssetDepreciatedUsingDoubleDeclining(asset))
      return false;
    
    if (!isAssetHasValidUseLifeYear(asset)){
      return false;
    }
    
    return true;
  }

  private boolean isAssetHasValidUseLifeYear(Asset asset) {
    
    if (!isAssetHasValidAnnualDepreciationPercentage(asset))
      return false;
    
    Long assetTimeSpanInMonth = asset.getUsableLifeMonths();    
    if (assetTimeSpanInMonth==null)
      return false;
    
    int annualdepreciationpercentage = asset.getAnnualDepreciation().intValue();
    
    if (annualdepreciationpercentage==50 && assetTimeSpanInMonth!=48)
      return false;
    if (annualdepreciationpercentage==25 && assetTimeSpanInMonth!=96)
      return false;
    if (annualdepreciationpercentage==20 && assetTimeSpanInMonth!=120)
      return false;
    
    return true;
  }

  private boolean isAssetHasValidAnnualDepreciationPercentage(Asset asset) {
    if (asset.getAnnualDepreciation()==null)
      return false;
    
    int annualdepreciationpercentage = asset.getAnnualDepreciation().intValue();
    if (annualdepreciationpercentage<0)
      return false;
    
    switch (annualdepreciationpercentage) {
      case 50:
      case 25:
      case 20:
        return true;

      default:
        return false;
    }
  }

  private boolean isAssetDepreciatedUsingDoubleDeclining(Asset asset) {
    DepreciationMethod depreciationmethod = DepreciationMethod.valueOf(asset.getDepreciationType());
    if (depreciationmethod == DepreciationMethod.CAM_DOUBLEDECLINING) {
      return true;
    }
    
    return false;
  }

  private void removePendingAmortizationLine(Asset asset) {
    
    List<String> amortizationLineTobeDeleted = asset.getFinancialMgmtAmortizationLineList()
      .stream()
      .filter(x -> !x.getAmortization().isProcessed())
      .map(x -> x.getId())
      .collect(Collectors.toList());
    
    String sqlDeleteStatement = String.format(
        "delete from a_amortizationline where a_amortizationline_id in  (%s)",
        amortizationLineTobeDeleted.stream()
            .collect(Collectors.joining(", ")));

    try {
      Connection connection = new DalConnectionProvider().getConnection();
      PreparedStatement ps = connection.prepareStatement(sqlDeleteStatement);

      ps.execute();
      
      connection.commit();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
