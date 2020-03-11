package org.wirabumi.cam.process;

import java.time.LocalDate;
import java.time.Period;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.financialmgmt.assetmgmt.Asset;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;

public class AssetLifeToDateBackground extends DalBaseProcess {

	@Override
	protected void doExecute(ProcessBundle bundle) throws Exception {
		//hitung life to date atase sebuah asset
		Calendar cal = Calendar.getInstance();
		LocalDate nowLocalDate = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)+1, cal.get(Calendar.DAY_OF_MONTH));
		
		OBCriteria<Asset> assetCriteria = OBDal.getInstance().createCriteria(Asset.class);
		List<Asset> assetList = assetCriteria.list();
		for (Asset asset : assetList){
			
			if (asset.getSearchKey().equalsIgnoreCase("BRG/A/INVENTARIS KANTOR/0317/2005"))
				System.err.println("debug");
			
			Date startdate = asset.getInServiceDate();
			if (startdate==null)
				continue;
		
			cal.setTime(startdate);
			LocalDate startLocalDate = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)+1, cal.get(Calendar.DAY_OF_MONTH)); 
			
			Period age = Period.between(startLocalDate, nowLocalDate);
			int year = age.getYears();
			int month = age.getMonths();
			String lifetodate = year+" year(s) and "+month+" month(s)";
			asset.setCamLifetodate(lifetodate);
			OBDal.getInstance().save(asset);
			
		}
		
		OBDal.getInstance().commitAndClose();

	}

}
