package org.wirabumi.cam.ad_callout;

import javax.servlet.ServletException;

import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.ad_callouts.SimpleCallout;
import org.openbravo.model.financialmgmt.accounting.Costcenter;
import org.openbravo.model.financialmgmt.assetmgmt.Asset;
import org.wirabumi.cam.Location;

public class WorkOrderCallout extends SimpleCallout {

	@Override
	protected void execute(CalloutInfo info) throws ServletException {
		String lastchanged = info.getLastFieldChanged();
		if (!lastchanged.equals("inpaAssetId"))
			return;
		
		String assetID = info.getStringParameter("inpaAssetId");
		if (assetID==null || assetID.isEmpty())
			return;
		
		Asset asset = OBDal.getInstance().get(Asset.class, assetID);
		Location location = asset.getCamLocation();
		Costcenter costcenter = asset.getCamCostcenter();
		
		info.addResult("inpcamLocationId", location.getId());
		info.addResult("inpcCostcenterId", costcenter.getId());
		
	}

}
