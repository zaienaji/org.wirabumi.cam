package org.wirabumi.cam.process;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.model.financialmgmt.assetmgmt.Amortization;
import org.openbravo.model.financialmgmt.assetmgmt.AmortizationLine;
import org.openbravo.model.financialmgmt.assetmgmt.Asset;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;
import org.openbravo.service.db.DalConnectionProvider;

import com.google.common.collect.HashBasedTable;

public class AssetAmortizationProcess extends DalBaseProcess {
	
	final SimpleDateFormat df = new SimpleDateFormat("dd-MM-yyyy");
	final BigDecimal seratus = new BigDecimal(100);
	final private Logger logger = Logger.getLogger(this.getClass());

	@Override
	protected void doExecute(ProcessBundle bundle) throws Exception {
		
		OBError msg = new OBError();
		
		final String strAssetId = (String) bundle.getParams().get("A_Asset_ID");
		if (strAssetId==null || strAssetId.isEmpty() || strAssetId.equalsIgnoreCase(""))
		{
			logger.debug("asset id is null");
			return; //null asset id
		}
			
		
		final Asset asset = (Asset) OBDal.getInstance().getProxy(Asset.ENTITY_NAME, strAssetId);
		if (asset==null)
		{
			logger.debug("invalid asset id: "+strAssetId);
			return; //invalid asset id
		}
			
		
		if (!asset.isDepreciate()){
			msg.setType("Warning");
			msg.setTitle("Warning");
			msg.setMessage("asset is not deprecited, no amortization created");
			bundle.setResult(msg);
			logger.debug("asset is not depreciated. asset id: "+strAssetId);
			return; //asset is not depreciated
		}
		
		//validate asset, is depreciation for this asset eligible to calculate?
		String errorMsg = isAssetEligibleForDepreciation(asset);
		if (errorMsg!=null)
		{
			//asset is not eligible for depecication, return with error message
			msg.setType("Error");
			msg.setTitle("Error");
			msg.setMessage(errorMsg);
			bundle.setResult(msg);
			return;
		}
		
		//create asset depreciation bean for convenient calculation
		ConnectionProvider conn = new DalConnectionProvider();
		Connection connection = conn.getConnection();
		AssetDepreciationBean adb = new AssetDepreciationBean(asset, connection);
		
		//TODO for now, if depreciation method is straight-line, then use existing class (from core module)
		if (adb.depreciationtype.equals("LI")){
			logger.debug("depreciation method is liniter, hence execute core module logic for generation of depreciation lines");
			AssetLinearDepreciationMethodProcess existingclass = new AssetLinearDepreciationMethodProcess();
			existingclass.doExecute(bundle);
			return;
		}
		
		//load all not-posted amortization header
		//string orgID VS Date --> amortization OB object
		logger.debug("get not-posted amortization header");
		HashBasedTable<String, Date, Amortization> amortizationMap = getUnpostedAmortization(asset, connection); 
		
		logger.debug("remove not-posted amortization header");
		RemoveUnpostedAmortizationBean rua = removeUnpostedAmortization(asset, connection);
		OBDal.getInstance().getConnection().commit();
		adb.postedDepreciation=rua.postedAmortization;
		adb.depreciatedAmount=rua.depreciatedAmount;
		adb.lastDepreciationAmount=rua.lastDepreciationAmount;
		BigDecimal bookvalue = asset.getAssetValue().subtract(asset.getPreviouslyDepreciatedAmt()).subtract(asset.getResidualAssetValue())
				.subtract(rua.depreciatedAmount);
		adb.bookvalue=bookvalue;
		
		//loop for amortization creation
		OBContext.setAdminMode();
		List<AmortizationLine> alList = null;
		if (adb.depreciationtype.equals("CAM_DOUBLEDECLINING")) {
			logger.debug("create amortization line using double declining method");
			alList = createAmortizationLineDoubleDeclining(adb, amortizationMap, bookvalue);
		}
		else {
			logger.debug("create amortization line using straight line method");
			alList = createAmortizationLineStraight(adb, amortizationMap, bookvalue);
		}
			
		BigDecimal currentdepreciation = BigDecimal.ZERO;
		if (alList!=null)
			for (AmortizationLine al : alList){
				OBDal.getInstance().save(al);
				currentdepreciation = currentdepreciation.add(al.getAmortizationAmount());
			}
		currentdepreciation=currentdepreciation.add(adb.depreciatedAmount);
		adb.asset.setDepreciatedPlan(currentdepreciation);
		OBDal.getInstance().save(adb.asset);
		OBDal.getInstance().commitAndClose();
		logger.debug("current depreciation plan have been updated and commited");
		OBContext.restorePreviousMode();
		
		msg.setTitle("Success");
		msg.setType("Success");
		msg.setMessage(adb.getIteration()+" Amortization(s) created successfully.");
		bundle.setResult(msg);

	}

	private String isAssetEligibleForDepreciation(Asset asset) {
		StringBuilder sb = new StringBuilder();
		Date depreciationStartDate = asset.getDepreciationStartDate();
		if (depreciationStartDate==null)
			sb.append("depreciation date is empty.").append(System.lineSeparator());

		Currency currency = asset.getCurrency();
		if (currency==null)
			sb.append("currency date is empty.").append(System.lineSeparator());

		String depreciationtype = asset.getDepreciationType(); //LI atau CAM_DOUBLEDECLINING
		String calculationtype = asset.getCalculateType(); //PE (percentage) atau TI (time)
		BigDecimal annualdepreciationpercentage = asset.getAnnualDepreciation(); //tidak mandatory
		if (depreciationtype.equals("CAM_DOUBLEDECLINING") && 
				(annualdepreciationpercentage==null || annualdepreciationpercentage.compareTo(BigDecimal.ZERO)<=0))
			sb.append("deprecication type is double declininig, but annual depreication percentage is empty/zero/negative.").append(System.lineSeparator());
		if (calculationtype.equals("PE") && 
				(annualdepreciationpercentage==null || annualdepreciationpercentage.compareTo(BigDecimal.ZERO)<=0))
			sb.append("calculation type is percentage, but annual depreication percentage is empty/zero/negative.").append(System.lineSeparator());

		String depreciationperiod = asset.getAmortize(); //MO (monthly) atau YE (yearly)
		
		Long uselifeyear = asset.getUsableLifeYears();
		if (uselifeyear==null) uselifeyear=new Long(0);
		Long uselifemonth = asset.getUsableLifeMonths();
		if (uselifemonth==null)
			uselifemonth=new Long(0);
		if (depreciationperiod.equals("MO") && uselifemonth<=0)
			sb.append("amortize (depreciation period) monthly, but use life month is empty/zero/negative.").append(System.lineSeparator());
		if (depreciationperiod.equals("YE") && uselifeyear<=0)
			sb.append("amortize (depreciation period) yearly, but use life year is empty/zero/negative.").append(System.lineSeparator());

		if (sb.length()>0){
			//ada error saat validasi, tampilkan error message
			return sb.toString();
		}
		
		return null;
	}

	private List<AmortizationLine> createAmortizationLineStraight(AssetDepreciationBean adb,
			HashBasedTable<String, Date, Amortization> amortizationMap, BigDecimal bookvalue) {
		// TODO Auto-generated method stub
		return null;
	}

	private List<AmortizationLine> createAmortizationLineDoubleDeclining(AssetDepreciationBean adb,
			HashBasedTable<String, Date, Amortization> amortizationMap, BigDecimal bookvalue) {
		
		final String orgID = adb.asset.getOrganization().getId();
		final BigDecimal denominator = new BigDecimal(12);
		List<AmortizationLine> output = new ArrayList<AmortizationLine>();
		
		/*
		 * jumlah tahun depresiasi = use life year
		 * 	or (use life month / 12)
		 *  or (use life month / 12 + 1) jika use life month tidak habis dibagi 12
		 * dasar pengenaan depresiasi tahun 1 = asset value - prev depreciation - residual
		 * tarif depresiasi tahun 1= annual depreciation / 100% * dasar pengenaan depresiasi
		 * dasar pengenaan depresiasi tahun n, dimana n>1, adalah book value pada akhir tahun ke n-1
		 * tarif depresiasi tahun n, dimana n>1, dasar pengenaan depresiasi tahun n / 100% * dasar pengenaan depresiasi
		 * jika sampai dengan akhir use life month/year, ternyata masih ada sisa, maka sisa dimasukkan ke periode berikutnya.
		 * khusus untuk tahun terakhir, maka nilai buku dibagi dengan sisa bulan. misalnya depresiasi sd maret, maka depresiasi = nilai buku / 3. 
		 * 
		 * bagaimana jika start depreciation datenya maret 17 , sudah di posting sampai dengan juni 17?
		 * 	tarif depresiasi juli 17 sd des 17 = tarif depresiasi juni 17
		 *  tarif depresiasi jan 18 dan seterusnya ikut aturan.
		 * bagaimana jika start depreciation datenya maret 17 , sudah di posting sampai dengan juni 18?
		 *  tarif depresiasi juli 18 sd des 18 = tarif depresiasi juni 18
		 *  tarif depresiasi jan 19 dan seterusnya ikut aturan.
		 * bagaimana jika start depreciation datenya maret 17 , sudah di posting sampai dengan des 17?
		 *  tarif depresiasi jan 18 dan seterusnya ikut aturan
		 */
		
		BigDecimal assetValue = adb.asset.getAssetValue();
		BigDecimal prevDepreciation = adb.asset.getPreviouslyDepreciatedAmt();
		if (prevDepreciation==null)
			prevDepreciation=BigDecimal.ZERO;
		BigDecimal residualValue = adb.asset.getResidualAssetValue();
		if (residualValue==null)
			residualValue=BigDecimal.ZERO;
		BigDecimal dasarPengenaanDepresiasi = assetValue.subtract(prevDepreciation).subtract(residualValue);
		BigDecimal tarifdepresiasi = null;
		if (adb.postedDepreciation>0)
			tarifdepresiasi = adb.lastDepreciationAmount.multiply(denominator);
		else
			tarifdepresiasi = dasarPengenaanDepresiasi.multiply(adb.annualdepreciationpercentage).divide(seratus, 0, RoundingMode.HALF_DOWN);
		
		Calendar cal = Calendar.getInstance();
		Date lastdepreciationdate = adb.getDepreciationLastDate();
		cal.setTime(lastdepreciationdate);
		int lastdepreciationyear = cal.get(Calendar.YEAR);
		BigDecimal lastdepreicationdenominator = new BigDecimal(cal.get(Calendar.MONTH)+1); 
		
		cal.setTime(adb.nextdepreciationstartdate);
		long iteration = adb.getIteration();
		
		
		for (int i=0; i<iteration; i++){
			if (i==0){
				cal.set(Calendar.DAY_OF_MONTH, 1);
				if (adb.depreciationperiod.equals("YE"))
					cal.set(Calendar.MONTH, 0);
			}
				
			if (cal.get(Calendar.MONTH)==0 //januari, maka ganti tarif
					&& (i!=0 || adb.postedDepreciation>0)) //tidak mulai dari awal, kalau mulai dari awal tarif depresiasinya sudah bener, tidak perlu diupdate 
				tarifdepresiasi=bookvalue.multiply(adb.annualdepreciationpercentage).divide(seratus, 0, RoundingMode.HALF_DOWN);
			
			//get amortization header
			Date startdate = cal.getTime();
			Amortization amortization = null;
			if (amortizationMap.contains(orgID, startdate))
				amortization = amortizationMap.get(orgID, startdate);
			else{
				//calculate end period
				if (adb.depreciationperiod.equals("MO"))
					cal.add(Calendar.MONTH, 1);
				else
					cal.add(Calendar.YEAR, 1);
				cal.add(Calendar.DAY_OF_MONTH, -1);
				Date enddate = cal.getTime();
				cal.setTime(startdate);
				
				//bikin header baru
				amortization = OBProvider.getInstance().get(Amortization.class);
				amortization.setOrganization(adb.asset.getOrganization());
				amortization.setName(df.format(enddate));
				amortization.setEndingDate(enddate);
				amortization.setStartingDate(startdate);
				amortization.setAccountingDate(enddate);
				amortization.setCurrency(adb.currency);
				OBDal.getInstance().save(amortization);
				amortizationMap.put(orgID, startdate, amortization);
			}
			
			if (i!=(iteration-1)) {
				//not the last amortization
				//last amortization
				//TODO dikarenakan prorate belum diimplementasikan, maka faktor pembagi dalam setahun masih 12
				BigDecimal amortizationamount;
				if (cal.get(Calendar.YEAR)==lastdepreciationyear && cal.get(Calendar.MONTH)==0)
					tarifdepresiasi=bookvalue;
				if (cal.get(Calendar.YEAR)==lastdepreciationyear)
					amortizationamount = tarifdepresiasi.divide(lastdepreicationdenominator, 0, RoundingMode.HALF_DOWN);
				else
					amortizationamount = tarifdepresiasi.divide(denominator, 0, RoundingMode.HALF_DOWN);
				BigDecimal amortizationPercentage = amortizationamount.divide(dasarPengenaanDepresiasi, 2, RoundingMode.HALF_DOWN).multiply(seratus);
				
				AmortizationLine al = OBProvider.getInstance().get(AmortizationLine.class);
				al.setOrganization(amortization.getOrganization());
				al.setAmortization(amortization);
				al.setAsset(adb.asset);
				al.setAmortizationPercentage(amortizationPercentage);
				al.setAmortizationAmount(amortizationamount);
				al.setCurrency(adb.currency);
				al.setProject(adb.asset.getProject());
				al.setCostcenter(adb.asset.getCamCostcenter());
				output.add(al);
				
				//prepare for next loop
				if (adb.depreciationperiod.equals("MO"))
					cal.add(Calendar.MONTH, 1);
				else
					cal.add(Calendar.YEAR, 1);
				
				bookvalue = bookvalue.subtract(amortizationamount);
			} else {
				if (bookvalue.compareTo(BigDecimal.ZERO)!=0){
					//book value masih ada sisa, maka buat amortisasi pelengkap di akhir
					BigDecimal amortizationPercentage = bookvalue.divide(dasarPengenaanDepresiasi, 2, RoundingMode.HALF_DOWN).multiply(seratus);
					AmortizationLine al = OBProvider.getInstance().get(AmortizationLine.class);
					al.setOrganization(amortization.getOrganization());
					al.setAmortization(amortization);
					al.setAsset(adb.asset);
					al.setAmortizationPercentage(amortizationPercentage);
					al.setAmortizationAmount(bookvalue);
					al.setCurrency(adb.currency);
					al.setProject(adb.asset.getProject());
					al.setCostcenter(adb.asset.getCamCostcenter());
					output.add(al);
				}
				break;	
			}
		}
		
		return output;
		
	}
	
	private HashBasedTable<String, Date, Amortization> getUnpostedAmortization(Asset asset, Connection connection) {
		HashBasedTable<String, Date, Amortization> output = HashBasedTable.create();
		String orgID = asset.getOrganization().getId();
		
		String sql = "select distinct a.a_amortization_id, a.startdate"
				+ " from a_amortization a"
				+ " where a.ad_org_id=?"
				+ " and a.processed='N'"
				+ " and a.a_amortization_id is not null";
		try {
			PreparedStatement ps = connection.prepareStatement(sql);
			ps.setString(1, orgID);
			ResultSet rs = ps.executeQuery();
			while (rs.next()){
				String amortizationID = rs.getString("a_amortization_id");
				Amortization amortization = OBDal.getInstance().get(Amortization.class, amortizationID);
				Date startdate = rs.getDate("startdate");
				output.put(orgID, startdate, amortization);
			}
		} catch (SQLException e) {
			throw new OBException(e.getMessage());
		}

		return output;
	}

	private RemoveUnpostedAmortizationBean removeUnpostedAmortization(Asset asset, Connection connection) {
		
		Integer postedAmortization=0;
		BigDecimal depreciatedAmount=BigDecimal.ZERO;
		BigDecimal lastDepreciationAmount=BigDecimal.ZERO; 
		Date lastamortization=null;
		for (AmortizationLine al : asset.getFinancialMgmtAmortizationLineList()){
			Amortization a = al.getAmortization();
			if (a.isProcessed()){
				postedAmortization++;
				depreciatedAmount=depreciatedAmount.add(al.getAmortizationAmount());
				if (lastamortization==null || lastamortization.before(a.getEndingDate())){
					lastamortization=a.getEndingDate();
					lastDepreciationAmount=al.getAmortizationAmount();
				}
			}
		}
		
		String sql = "delete from a_amortizationline"
				+ " where a_asset_id=?"
				+ " and exists (select 1 from a_amortization"
				+ "			where a_amortization_id=a_amortizationline.a_amortization_id"
				+ "			and a_amortization.processed='N')";
		try {
			PreparedStatement ps = connection.prepareStatement(sql);
			ps.setString(1, asset.getId());
			ps.executeUpdate();
			
		} catch (SQLException e) {
			throw new OBException(e.getMessage());
		}
		
		RemoveUnpostedAmortizationBean output = new RemoveUnpostedAmortizationBean();
		output.postedAmortization=postedAmortization;
		output.depreciatedAmount=depreciatedAmount;
		output.lastDepreciationAmount=lastDepreciationAmount;
		
		return output;
		
	}

	/**
	 * get maximum start date of processed depreciation record for this asset. it will gather all processed depreciation record and return it's maximum start date.
	 * @param asset asset
	 * @param connection db connection
	 * @return maximum start date of processed depreciation
	 */
	private Date getMaxPostedDepreciation(Asset asset, Connection connection) {
		String sql = "select max(a.startdate) as maxstardate"
				+ " from a_amortization a"
				+ " inner join a_amortizationline b on b.a_amortization_id=a.a_amortization_id"
				+ " where b.a_asset_id=?"
				+ " and a.processed='Y'";
		try {
			PreparedStatement ps = connection.prepareStatement(sql);
			ps.setString(1, asset.getId());
			ResultSet rs  = ps.executeQuery();
			while(rs.next()){
				Date maxstardate = rs.getDate("maxstardate");
				return maxstardate;
			}
		} catch (SQLException e) {
			throw new OBException(e.getMessage());
		}
		return null;
	}

	private class AssetDepreciationBean{
		Asset asset;
		Date nextdepreciationstartdate;
		Currency currency;
		String depreciationtype; //LI atau CAM_DOUBLEDECLINING
		String calculationtype; //PE (percentage) atau TI (time)
		String depreciationperiod; //MO (monthly) atau YE (yearly)
		BigDecimal annualdepreciationpercentage;
		int uselifeyear, uselifemonth;
		boolean is30DayMonth; //not implemented yet
		int startDepreciationDateToEndOfMonth=0; //not implemented yet
		long postedDepreciation;
		BigDecimal depreciatedAmount;
		BigDecimal bookvalue;
		BigDecimal lastDepreciationAmount;
		
		//the big constructor
		public AssetDepreciationBean(Asset asset, Connection connection) {
			//get depreciation information and it's validity
			Date depreciationStartDate = asset.getDepreciationStartDate();
			Currency currency = asset.getCurrency();
			String depreciationtype = asset.getDepreciationType(); //LI atau CAM_DOUBLEDECLINING
			String calculationtype = asset.getCalculateType(); //PE (percentage) atau TI (time)
			BigDecimal annualdepreciationpercentage = asset.getAnnualDepreciation(); //tidak mandatory
			String depreciationperiod = asset.getAmortize(); //MO (monthly) atau YE (yearly)
			Long uselifeyear = asset.getUsableLifeYears();
			if (uselifeyear==null)
				uselifeyear=new Long(0);
			Long uselifemonth = asset.getUsableLifeMonths();
			if (uselifemonth==null) 
				uselifemonth=new Long(0);
						
			//get max posted depreciation, jika ada, maka start date nya adalah end date posted depreciation + 1 periode amortization
			Date maxposteddepreciationStartDate = getMaxPostedDepreciation(asset, connection);
			Date nextdepreciationstartdate;
			if (maxposteddepreciationStartDate!=null){
				Calendar cal = Calendar.getInstance();
				cal.setTime(maxposteddepreciationStartDate);
				if (depreciationperiod.equals("MO"))
					cal.add(Calendar.MONTH, 1);
				else
					cal.add(Calendar.YEAR, 1);
				nextdepreciationstartdate=cal.getTime();
			} else {
				Calendar cal = Calendar.getInstance();
				cal.setTime(depreciationStartDate);
				int a = cal.get(Calendar.DAY_OF_MONTH);
				cal.add(Calendar.MONTH, 1);
				cal.add(Calendar.DAY_OF_MONTH, -1);
				int b = cal.get(Calendar.DAY_OF_MONTH);
				this.startDepreciationDateToEndOfMonth=b-a+1;
				nextdepreciationstartdate=depreciationStartDate;
			}
			
			boolean is30DayMonth = asset.isEveryMonthIs30Days();
			
			this.asset=asset;
			this.nextdepreciationstartdate=nextdepreciationstartdate;
			this.currency=currency;
			this.depreciationtype=depreciationtype;
			this.calculationtype=calculationtype;
			this.depreciationperiod=depreciationperiod;
			this.annualdepreciationpercentage=annualdepreciationpercentage;
			this.uselifeyear=uselifeyear.intValue();
			this.uselifemonth=uselifemonth.intValue();
			this.is30DayMonth=is30DayMonth;
			
		}
		
		/**
		 * calculate how many depreciation record will be created. if the asset does not has any processed depreciation, 
		 * it will return use life year/month. if any it will return use life year/month subtracted by processed depreciation record.  
		 * @return how many depreciation record will be created
		 */
		public long getIteration(){
			long iteration = 0;
			if (depreciationperiod.equals("MO"))
				iteration=uselifemonth-postedDepreciation;
			else
				iteration=uselifeyear-postedDepreciation;
			
			return iteration;
		}
		
		/**
		 * determine last depreciation date based on use life year/month add to start depreciation date
		 * @return last depreciation date
		 */
		public Date getDepreciationLastDate(){
			if (asset==null)
				throw new OBException("asset property in AssetDepreciationBean is null");
			Date depreciationstartdate = asset.getDepreciationStartDate();
			if (depreciationstartdate==null)
				throw new OBException("asset property in AssetDepreciationBean has no depreciation start date");
			Calendar cal = Calendar.getInstance();
			cal.setTime(asset.getDepreciationStartDate());
			if (depreciationperiod.equals("MO")){
				if (uselifemonth==0)
					throw new OBException("asset property in AssetDepreciationBean has monthly depreciation period but no use life month defined.");
				cal.add(Calendar.MONTH, uselifemonth);				
			} else if (depreciationperiod.equals("YE")) {
				if (uselifeyear==0)
					throw new OBException("asset property in AssetDepreciationBean has yearly depreciation period but no use life year defined.");
				cal.add(Calendar.YEAR, uselifeyear);
			} else
				throw new OBException("asset property in AssetDepreciationBean has invalid depreciation period: "+depreciationperiod);
			
			cal.add(Calendar.DAY_OF_MONTH, -1);
			return cal.getTime();
		}
	}
	
	private class RemoveUnpostedAmortizationBean{
		int postedAmortization;
		BigDecimal depreciatedAmount, lastDepreciationAmount;
	}

}
