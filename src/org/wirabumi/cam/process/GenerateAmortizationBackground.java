package org.wirabumi.cam.process;

import java.util.List;

import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.financialmgmt.assetmgmt.Asset;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;
import org.wirabumi.cam.utility.CamUtility;

public class GenerateAmortizationBackground extends DalBaseProcess {

	@Override
	protected void doExecute(ProcessBundle bundle) throws Exception {
		OBCriteria<Asset> assetC = OBDal.getInstance().createCriteria(Asset.class);
		List<Asset> assetList = assetC.list();
		for (Asset asset : assetList) {
			System.out.println("asset id "+asset.getId()+" name "+asset.getName());
			CamUtility.generateAssetAmortization(asset);
		}
	}

}
