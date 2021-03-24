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

  @Override
  protected void doExecute(ProcessBundle bundle) throws Exception {

    final Asset asset = getAsset(bundle);
    Contract.require(asset!=null, "can not find asset");
    
    if (!asset.isDepreciate()){
    	bundle.setResult(OBErrorBuilder.buildMessage(null, "Warning", "asset is not set to be depreciated. No amortization generated for this asset."));
    }
    
    Result validation = isAssetEligibleForDepreciation(asset);
    Contract.require(!validation.isError(), validation.getErrorMessage());
    
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
    
    //TODO implement this
    /*
     * 
     * loop sd habis tahunnya:
     *   tentukan tarifnya, jika tahun terakhir, maka tarif = sisa aset value dibagi dengan sisa bulan
     *   loop sd end of year
     *     buat amortization
     *     consume asset value
     * 
     * commit
     */
    
    removePendingAmortizationLine(asset);
    OBDal.getInstance().refresh(asset);
    
    Map<Date, Amortization> unProcessedAmortization = getUnprocessedAmortizationHeader(asset.getOrganization());
    
    int yearLifeSpan = asset.getUsableLifeYears().intValue();
    BigDecimal annualDepreciation = asset.getAnnualDepreciation().divide(new BigDecimal(100), 2, RoundingMode.HALF_DOWN);
    BigDecimal assetValue = asset.getAssetValue();
    Calendar amortizationDate = Calendar.getInstance();
    amortizationDate.setTime(asset.getDepreciationStartDate());
    
    for (int yearTh=1; yearTh<=yearLifeSpan-1; yearTh++){
      BigDecimal tarif = 
          assetValue
            .divide(annualDepreciation, 2, RoundingMode.HALF_DOWN)
            .divide(new BigDecimal(12), 2, RoundingMode.HALF_DOWN);
      
      
      Date amortizationStartDate = amortizationDate.getTime();

      Amortization amortization = null;
      if (unProcessedAmortization.containsKey(amortizationStartDate)) {
        amortization = unProcessedAmortization.get(amortizationStartDate);
      } else {
        amortization = createAmortizationHeader(asset.getOrganization(), amortizationStartDate);
        unProcessedAmortization.put(amortizationStartDate, amortization);
      }

      AmortizationLine amortizationLine = createAmortizationLine(asset, amortization, tarif);

      OBDal.getInstance().save(amortizationLine);
      
    }
    
    OBDal.getInstance().commitAndClose();

  }
  
  private AmortizationLine createAmortizationLine(Asset asset, Amortization amortization,
      BigDecimal tarif) {
    // TODO Auto-generated method stub
    return null;
  }

  private Amortization createAmortizationHeader(Organization organization,
      Date amortizationStartDate) {
    // TODO Auto-generated method stub
    return null;
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

    Long uselifeyear = asset.getUsableLifeYears() != null ? asset.getUsableLifeYears() : 0;
    Long uselifemonth = asset.getUsableLifeMonths() != null ? asset.getUsableLifeMonths() : 0;

    DepreciationPeriod period = DepreciationPeriod.valueOf(asset.getAmortize());

    return RuleBuilder
        .Create(asset.getDepreciationStartDate() != null, "depreciation date is empty.")
        .and(asset.getCurrency() != null, "currency date is empty.")
        .and(asset.isDepreciate(), "asset is not deprecited, no amortization created")
        .and(ifDepreciationTypeIsDoubleDecliningThenValidate(asset),
            "deprecication type is double declininig, but annual depreication percentage is empty/zero/negative.")
        .and(ifCalculcationTypeIsPercentageThenValidate(asset),
            "calculation type is percentage, but annual depreication percentage is empty/zero/negative.")
        .andNot(period == DepreciationPeriod.MO && uselifemonth <= 0,
            "amortize (depreciation period) monthly, but use life month is empty/zero/negative.")
        .andNot(period == DepreciationPeriod.YE && uselifeyear <= 0,
            "amortize (depreciation period) yearly, but use life year is empty/zero/negative.")
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

    DepreciationMethod depreciationmethod = DepreciationMethod.valueOf(asset.getDepreciationType());

    if (depreciationmethod != DepreciationMethod.CAM_DOUBLEDECLINING) {
      return true;
    }

    BigDecimal annualdepreciationpercentage = asset.getAnnualDepreciation();

    return ObjectUtils.compare(annualdepreciationpercentage, BigDecimal.ZERO) >= 0;

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
