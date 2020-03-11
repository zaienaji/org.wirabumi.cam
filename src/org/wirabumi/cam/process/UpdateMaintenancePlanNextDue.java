package org.wirabumi.cam.process;

import java.util.Calendar;
import java.util.Date;

import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.manufacturing.maintenance.MaintenanceSchedule;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;
import org.wirabumi.cam.WorkOrder;


public class UpdateMaintenancePlanNextDue extends DalBaseProcess {

	@Override
	protected void doExecute(ProcessBundle bundle) throws Exception {
		/*
		 * 1. jika ada maintenance plan yang belum ada nextdue nya --> isi
		 * 2. jika ada maintenance plan yang tanggal nextdue nya terlewati -->
		 * 	2.1 update
		 * 	2.2 buatkan work order
		 */
		
		OBCriteria<MaintenanceSchedule> msCriteria = OBDal.getInstance().createCriteria(MaintenanceSchedule.class);
		msCriteria.add(Restrictions.isNull(MaintenanceSchedule.PROPERTY_CAMNEXTDUE));
		
		for (MaintenanceSchedule ms : msCriteria.list()){
			//isi next due pada maintenance plan
			String frequency = ms.getCamFrequency();
			Long freqamt = ms.getCamFrequencyamt();
			Date startdate = ms.getCamStartdate();
			Calendar cal = Calendar.getInstance();
			cal.setTime(startdate);
			if (frequency.equalsIgnoreCase("1")){
				//weekly
				cal.add(Calendar.WEEK_OF_YEAR, freqamt.intValue());
			} else if (frequency.equalsIgnoreCase("2")){
				//monthly
				cal.add(Calendar.MONTH, freqamt.intValue());
			} else if (frequency.equalsIgnoreCase("3")){
				//yearly
				cal.add(Calendar.YEAR, freqamt.intValue());
			}
			
			Date nextdue = cal.getTime();
			ms.setCamNextdue(nextdue);
			OBDal.getInstance().save(ms);
		}
		
		OBDal.getInstance().getConnection().commit(); //commit next due
		
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.MILLISECOND, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.HOUR, 0);
		Date now = cal.getTime();
		
		msCriteria = OBDal.getInstance().createCriteria(MaintenanceSchedule.class);
		for (MaintenanceSchedule ms : msCriteria.list()){
			
			OBDal.getInstance().refresh(ms);
			Date nextdue = ms.getCamNextdue();
			if (nextdue.after(now))
				continue;
			
			//nextdue sudah lewat, maka buatkan work order
			WorkOrder wo = OBProvider.getInstance().get(WorkOrder.class);
			wo.setDocumentNo("<>");
			wo.setScheduledMaintenance(ms);
			wo.setOrganization(ms.getOrganization());
			wo.setStartingDate(nextdue);
			OBDal.getInstance().save(wo);
			
			//nextdue sudah lewat dan sudah dibuatkan wo, maka update next due
			String frequency = ms.getCamFrequency();
			Long freqamt = ms.getCamFrequencyamt();
			cal.setTime(nextdue);
			if (frequency.equalsIgnoreCase("1")){
				//weekly
				cal.add(Calendar.WEEK_OF_YEAR, freqamt.intValue());
			} else if (frequency.equalsIgnoreCase("2")){
				//monthly
				cal.add(Calendar.MONTH, freqamt.intValue());
			} else if (frequency.equalsIgnoreCase("3")){
				//yearly
				cal.add(Calendar.YEAR, freqamt.intValue());
			}
			
			nextdue = cal.getTime();
			ms.setCamNextdue(nextdue);
			OBDal.getInstance().save(ms);
			
		}

	}

}
