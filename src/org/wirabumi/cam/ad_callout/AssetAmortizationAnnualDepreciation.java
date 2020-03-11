package org.wirabumi.cam.ad_callout;

import javax.servlet.ServletException;

import org.openbravo.erpCommon.ad_callouts.SimpleCallout;

public class AssetAmortizationAnnualDepreciation extends SimpleCallout {

	@Override
	protected void execute(CalloutInfo info) throws ServletException {
		//make sure use life year/month match to annuan depreciation rate 
		String strcalctype = info.getStringParameter("inpamortizationcalctype");
		if (strcalctype==null || !strcalctype.equals("PE"))
			return;
		
		int uselifemonth = 0, uselifeyear = 0;
		String strannualdepreciationrate = info.getStringParameter("inpannualamortizationpercentage");
		if (strannualdepreciationrate.equals("50")){
			uselifeyear=4;
		} else if (strannualdepreciationrate.equals("25")) {
			uselifeyear=8;
		} else if (strannualdepreciationrate.equals("20")) {
			uselifeyear=10;
		}
		
		uselifemonth=uselifeyear*12;
		
		String strUselifemonth = Integer.valueOf(uselifemonth).toString();
		String strUselifeyear = Integer.valueOf(uselifeyear).toString();
		
		info.addResult("inpuselifemonths", strUselifemonth);
		info.addResult("inpuselifeyears", strUselifeyear);
		
	}

}
