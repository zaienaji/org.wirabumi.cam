package org.wirabumi.cam.event;

import java.util.List;

import javax.enterprise.event.Observes;

import org.apache.log4j.Logger;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.model.financialmgmt.assetmgmt.Asset;
import org.wirabumi.cam.WorkOrder;
import org.wirabumi.cam.WorkOrderAsset;

public class WorkOrderEventHandler extends EntityPersistenceEventObserver {
	private static Entity[] entities = { ModelProvider.getInstance().getEntity(WorkOrder.ENTITY_NAME) };
	protected Logger logger = Logger.getLogger(this.getClass());

	@Override
	protected Entity[] getObservedEntities() {
		return entities;
	}
	
	public void onSave(@Observes EntityNewEvent event) {
		if (!isValidEvent(event)) {
			return;
		}
		
	    final Entity woEntity = ModelProvider.getInstance().getEntity(WorkOrder.ENTITY_NAME);
		
		//cek apakah wo header ada assetnya
		WorkOrder wo = (WorkOrder) event.getTargetInstance();
		Asset asset = wo.getAsset();
		if (asset==null)
			return;
		
		//iya ada asset-nya, maka buatlah 1 record di sub tab asset,
		//dengan asset yg sama dengan di header
		WorkOrderAsset woAsset = OBProvider.getInstance().get(WorkOrderAsset.class);
		woAsset.setWorkOrder(wo);
		woAsset.setOrganization(wo.getOrganization());
		woAsset.setAsset(asset);
		woAsset.setOldCostCenter(asset.getCamCostcenter());
		woAsset.setOldLocation(asset.getCamLocation());
		woAsset.setAssetmovement(false);
		woAsset.setAssetOpname(false);
		woAsset.setDisposed(false);
		
		//simpan woAsset dengan gaya business event handler
		final Property woAssetProperty = woEntity
		        .getProperty(WorkOrder.PROPERTY_CAMWORKORDERASSETLIST);
		@SuppressWarnings("unchecked")
		final List<Object> woAssets = (List<Object>) event.getCurrentState(woAssetProperty);
		woAssets.add(woAsset);
		
	}

}
