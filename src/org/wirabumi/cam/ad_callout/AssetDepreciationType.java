package org.wirabumi.cam.ad_callout;

import javax.servlet.ServletException;

import org.openbravo.erpCommon.ad_callouts.SimpleCallout;

public class AssetDepreciationType extends SimpleCallout {

	@Override
	protected void execute(CalloutInfo info) throws ServletException {
		//make sure amortization calc type set to percentage when amortization type is double declining
		String depreciationtype = info.getStringParameter("inpamortizationtype");
		if (depreciationtype.equals("CAM_DOUBLEDECLINING"))
			info.addResult("inpamortizationcalctype", "PE");		
	}

}
