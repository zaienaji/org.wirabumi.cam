package org.wirabumi.cam.process;

import java.util.List;

import org.openbravo.model.financialmgmt.assetmgmt.Amortization;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;
import org.wirabumi.cam.utility.CamUtility;

public class DeleteAmortizationBackground extends DalBaseProcess {

	@Override
	protected void doExecute(ProcessBundle bundle) throws Exception {
		
		List<Amortization> amortizationList = CamUtility.getProcessedNotPostedAmortization();
		CamUtility.unprocessAmortizations(amortizationList);
		CamUtility.deleteUnPorcessedAmortization();

	}

}
