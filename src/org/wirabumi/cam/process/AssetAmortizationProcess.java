package org.wirabumi.cam.process;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.openbravo.base.util.ArgumentException;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.financialmgmt.assetmgmt.Amortization;
import org.openbravo.model.financialmgmt.assetmgmt.AmortizationLine;
import org.openbravo.model.financialmgmt.assetmgmt.Asset;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;

public class AssetAmortizationProcess extends DalBaseProcess {

  /**
   * generate asset amortization plan according to asset's depreciation settings
   */
  @Override
  protected void doExecute(ProcessBundle bundle) throws Exception {

    final Asset asset = getAsset(bundle);
    if (asset == null) {
      bundle.setResult(getObError(ResultType.Error, "can not find asset"));
      return;
    }

    Result validation = isAssetEligibleForDepreciation(asset);
    if (validation.isError()) {
      bundle.setResult(getObError(ResultType.Error, validation.getErrorMessage()));
      return;
    }

    if (isAssetDepreciationLinear(asset)) {
      runCoreModuleAssetAmortizationGenerationProcess(bundle);
      return;
    }

    removePendingAmortizationLine(asset);

    Map<Date, Amortization> unProcessedAmortization = getUnprocessedAmortizationHeader();
    BigDecimal[] amortizationAmount = getAmortizationAmountPerSequence();

    int processedAmortizationLine = getProcessedAmortizationCount(asset);

    Calendar calendar = Calendar.getInstance();
    calendar.setTime(asset.getDepreciationStartDate());
    calendar.add(Calendar.MONTH, processedAmortizationLine + 1);

    for (int seqNo = 0; seqNo < amortizationAmount.length; seqNo++) {

      Date amortizationStartDate = calendar.getTime();

      Amortization amortization = null;
      if (unProcessedAmortization.containsKey(amortizationStartDate)) {
        amortization = unProcessedAmortization.get(amortizationStartDate);
      } else {
        amortization = createAmortizationHeader(asset.getOrganization(), amortizationStartDate);
      }

      BigDecimal tarif = amortizationAmount[seqNo];
      AmortizationLine amortizationLine = createAmortizationLine(asset, amortization, tarif);

      OBDal.getInstance().save(amortizationLine);

    }

    OBDal.getInstance().commitAndClose();

    bundle.setResult(getObError(ResultType.Success, " Amortization(s) created successfully."));

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

  private int getProcessedAmortizationCount(Asset asset) {
    int result = 0;

    for (AmortizationLine amortization : asset.getFinancialMgmtAmortizationLineList()) {
      if (amortization.getAmortization().isProcessed()) {
        continue;
      }

      result++;
    }

    return result;
  }

  private BigDecimal[] getAmortizationAmountPerSequence() {
    // TODO Auto-generated method stub
    return null;
  }

  private Map<Date, Amortization> getUnprocessedAmortizationHeader() {
    // TODO Auto-generated method stub
    return null;
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

  private void removePendingAmortizationLine(Asset asset) throws SQLException {

    for (AmortizationLine amortization : asset.getFinancialMgmtAmortizationLineList()) {
      if (amortization.getAmortization().isProcessed()) {
        continue;
      }

      OBDal.getInstance().remove(amortization);
    }

    OBDal.getInstance().flush();
    OBDal.getInstance().getConnection().commit();
    OBDal.getInstance().refresh(asset);
  }

  private class AssetVo {
    Currency currency;
    DepreciationMethod method;
    DepreciationCalcType calculationType;
    DepreciationPeriod period;
    BigDecimal annualPercentage;
    int uselifeyear;
    int uselifemonth;
    Date startDepreciationDate;

    public AssetVo(Asset asset) {
      currency = asset.getCurrency();
      method = DepreciationMethod.valueOf(asset.getDepreciationType());
      calculationType = DepreciationCalcType.valueOf(asset.getCalculateType());
      period = DepreciationPeriod.valueOf(asset.getAmortize());
      annualPercentage = asset.getAnnualDepreciation();
      uselifeyear = asset.getUsableLifeYears() != null ? asset.getUsableLifeYears().intValue() : 0;
      uselifemonth = asset.getUsableLifeMonths() != null ? asset.getUsableLifeMonths().intValue()
          : 0;
      startDepreciationDate = asset.getDepreciationStartDate();
    }
  }

}
