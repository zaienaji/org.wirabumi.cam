package org.wirabumi.cam.ad_callout;

import javax.servlet.ServletException;

import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.ad_callouts.SimpleCallout;
import org.openbravo.model.financialmgmt.accounting.Costcenter;
import org.openbravo.model.financialmgmt.assetmgmt.Asset;
import org.wirabumi.cam.Location;

public class WorkOrderAssetCallout extends SimpleCallout {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	protected void execute(CalloutInfo info) throws ServletException {
		String lastChanged = info.getLastFieldChanged();
		if (lastChanged.equals("inpaAssetId")){
			//yang diubah adalah asset id, jadi jalankan proses lookup current/old location dan current/old cost center
			String assetID = info.getStringParameter("inpaAssetId", null);
			Asset asset = OBDal.getInstance().get(Asset.class, assetID);
			
			//proses cost center
			Costcenter costcenter = asset.getCamCostcenter();
			if (costcenter!=null)
				info.addResult("inpcOldcostcenterId", costcenter.getId());
			
			//proses asset location
			Location location = asset.getCamLocation();
			if (location!=null)
				info.addResult("inpcamOldlocationId", location.getId());
			
		}

	}

}
