package org.wirabumi.cam.event;

import javax.enterprise.event.Observes;

import org.apache.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.materialmgmt.transaction.InternalConsumption;
import org.wirabumi.cam.WorkOrder;

public class AssetPartConsumptionEventHandler extends EntityPersistenceEventObserver {
	private static Entity[] entities = { ModelProvider.getInstance().getEntity(InternalConsumption.ENTITY_NAME) };
	protected Logger logger = Logger.getLogger(this.getClass());


	@Override
	protected Entity[] getObservedEntities() {
		return entities;
	}
	
	public void onSave(@Observes EntityNewEvent event) {
		if (!isValidEvent(event)) {
			return;
		}
		
		//pastikan kalau dari asset part consumption, maka nomor WO harus ada
		InternalConsumption ic = (InternalConsumption) event.getTargetInstance();
		boolean assetPartConsumtion= ic.isCamIsassetpartconsumption();
		if (!assetPartConsumtion)
			return;
		
		WorkOrder wo = ic.getCamWorkorder();
		if (wo==null)
			throw new OBException("In asset part consumption, work order should not empty.");
		
	}

}
