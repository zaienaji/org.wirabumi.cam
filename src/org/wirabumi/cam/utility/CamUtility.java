package org.wirabumi.cam.utility;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;

import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.process.ProcessInstance;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.financialmgmt.assetmgmt.Amortization;
import org.openbravo.model.financialmgmt.assetmgmt.Asset;
import org.openbravo.scheduling.ProcessBundle;
import org.wirabumi.cam.process.AssetAmortizationProcess;

public class CamUtility {

	public static List<Amortization> getProcessedNotPostedAmortization(){
		OBCriteria<Amortization> amortizationC = OBDal.getInstance().createCriteria(Amortization.class);
		amortizationC.add(Restrictions.ne(Amortization.PROPERTY_POSTED, "Y")); //tidak posted
		amortizationC.add(Restrictions.eq(Amortization.PROPERTY_PROCESSED, true)); //sudah di process
		return amortizationC.list();
	}
	
	public static void unprocessAmortizations(List<Amortization> amortizationList) {
		final Process process = OBDal.getInstance().get(Process.class, "800134");
		
		for(Amortization amortization : amortizationList) {
			ProcessInstance pinstance = createProcessInstance(process, amortization.getId());
			callStoredProcedure("cam_amortization_process", pinstance);	
		}			
	}
	
	private static ProcessInstance createProcessInstance(Process process, String recordID) {
		final User user = OBContext.getOBContext().getUser();
		OBContext.setAdminMode();
		final ProcessInstance pinstance = OBProvider.getInstance().get(ProcessInstance.class);
		pinstance.setProcess(process);
		pinstance.setActive(true);
		pinstance.setRecordID(recordID);
		pinstance.setUserContact(user);

		// persist to the db
	    OBDal.getInstance().save(pinstance);
		OBContext.restorePreviousMode();

	    // flush, this gives pInstance an ID
	    OBDal.getInstance().flush();
	    
		return pinstance;
		
	}
	
	private static void callStoredProcedure(String procedureName, ProcessInstance pinstance) {
		String query = "SELECT * FROM "+procedureName+"(?)";
		// call the SP
	    try {
	      // first get a connection
	      final Connection connection = OBDal.getInstance().getConnection();
	      final PreparedStatement ps = connection.prepareStatement(query);
	      ps.setString(1, pinstance.getId());
	      ps.execute();
	    } catch (Exception e) {
	      throw new IllegalStateException(e);
	    }

	    // refresh the pInstance as the SP has changed it
	    OBDal.getInstance().getSession().refresh(pinstance);

	    OBContext.setAdminMode();
	    long exitstatus = pinstance.getResult();
	    if (exitstatus==0) { //0 failure, 1 success
	    	//rollback transaction
	    	OBDal.getInstance().rollbackAndClose();
	    	
	    	//return error message
	    	String errMessage = pinstance.getErrorMsg();
	    	throw new OBException(errMessage);
	    }
	    OBContext.restorePreviousMode();

	    //commit transaction
	    try {
			OBDal.getInstance().getConnection().commit();
		} catch (SQLException e) {
			e.printStackTrace();
			throw new OBException(e.getMessage());
		}
	}
	
	public static void deleteUnPorcessedAmortization() {
		OBCriteria<Amortization> amortizationC = OBDal.getInstance().createCriteria(Amortization.class);
		amortizationC.add(Restrictions.eq(Amortization.PROPERTY_PROCESSED, false));
		for (Amortization amortization : amortizationC.list())
			OBDal.getInstance().remove(amortization);
		OBDal.getInstance().commitAndClose();
	}
	
	public static void generateAssetAmortization(Asset asset) {
		VariablesSecureApp vars = RequestContext.get().getVariablesSecureApp(); 
		ProcessBundle bundle = new ProcessBundle("8C6B905EDA8742A0ABF4092902D54090", vars);
		HashMap<String, Object> params = new HashMap<String, Object>();
		params.put("A_Asset_ID", asset.getId());
		bundle.setParams(params);
		
		AssetAmortizationProcess amortizationprocess = new AssetAmortizationProcess();
		try {
			amortizationprocess.execute(bundle);
		} catch (Exception e) {
			e.printStackTrace();
			throw new OBException(e.getMessage());
		}
		
	}

}
