package org.wirabumi.cam.process;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.exception.NoConnectionAvailableException;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.financialmgmt.assetmgmt.Amortization;
import org.openbravo.model.financialmgmt.assetmgmt.AmortizationLine;
import org.openbravo.model.financialmgmt.assetmgmt.Asset;
import org.openbravo.service.db.DalConnectionProvider;
import org.wirabumi.cam.WorkOrder;
import org.wirabumi.cam.WorkOrderAsset;
import org.wirabumi.gen.oez.event.DocumentRoutingHandlerAction;

public class CompleteWorkOrderHandlerAction extends DocumentRoutingHandlerAction {
	private final String complete="co";
	private final String reactive="re";

  @Override
  public void doRouting(String adWindowId, String adTabId, String doc_status_to,
      VariablesSecureApp vars, List<String> recordId) {
	  
	  for (int i = 0; i < recordId.size(); i++) {
		  //loop for each asset
		  String workorderID = recordId.get(i);
		  if (workorderID==null || workorderID.isEmpty())
			  throw new OBException("work order ID is null or work order is empty");
		  WorkOrder wo = OBDal.getInstance().get(WorkOrder.class, workorderID);
		  if (wo==null)
			  throw new OBException(workorderID+" is not valid work order ID");
		  Date woDate = wo.getStartingDate();
		  List<WorkOrderAsset> woAssetList = wo.getCamWorkorderassetList();
		  if (woAssetList.size()==0)
			  continue;
		  for (WorkOrderAsset woAsset : woAssetList){
			  if (doc_status_to.equalsIgnoreCase(complete)){
				  Asset asset = woAsset.getAsset();
				  if (asset==null)
					  continue;

				  //implementation of asset movement
				  if (woAsset.isAssetmovement()){
					  //pindahkan location dan cost center
					  asset.setCamLocation(woAsset.getAssetLocation());
					  asset.setCamCostcenter(woAsset.getCostCenter());
					  OBDal.getInstance().save(asset);

					  //pindahkan next amortizationline
					  for (AmortizationLine amortizationline : asset.getFinancialMgmtAmortizationLineList()){
						  Date accountingdate = amortizationline.getAmortization().getAccountingDate();
						  if (accountingdate.before(woDate))
							  continue;
						  amortizationline.setCostcenter(woAsset.getCostCenter());
						  OBDal.getInstance().save(amortizationline);
					  }
				  } else if (woAsset.isDisposed()){
					  //implementation of asset disposal: asset.isdispose=Y
					  BigDecimal depreciatedamt = asset.getAssetValue().subtract(asset.getPreviouslyDepreciatedAmt()).subtract(asset.getResidualAssetValue());
					  asset.setDepreciatedPlan(depreciatedamt);
					  asset.setDepreciatedValue(depreciatedamt);
					  asset.setFullyDepreciated(true);
					  asset.setActive(false);
					  asset.setDisposed(true);
					  Date disposedate = woAsset.getWorkOrder().getStartingDate();
					  asset.setAssetDisposalDate(disposedate);
					  OBDal.getInstance().save(asset);  
					  
					  Calendar cal = Calendar.getInstance();
					  cal.setTime(disposedate);
					  cal.set(Calendar.DAY_OF_MONTH, 1);
					  cal.add(Calendar.MONTH, 1);
					  cal.add(Calendar.DAY_OF_MONTH, -1);
					  Date lastdepreciationdate = cal.getTime();
					  String strlastdepreciationdate = new SimpleDateFormat("yyyy-MM-dd").format(lastdepreciationdate);
					  
					  //remove unprocessed amortizationline
					  String sql = "delete from a_amortizationline"
					  		+ " where a_asset_id=?"
					  		+ " and exists (select 1 from a_amortization"
					  		+ " where a_amortization_id=a_amortizationline.a_amortization_id and a_amortization.processed='N'"
					  		+ "  and a_amortization.dateacct>'"+strlastdepreciationdate+"')";
					  ConnectionProvider conn = new DalConnectionProvider();
					  Connection connection;
					try {
						connection = conn.getConnection();
						PreparedStatement ps = connection.prepareStatement(sql);
						ps.setString(1, asset.getId());
						ps.executeUpdate();
						connection.commit();
					} catch (NoConnectionAvailableException e) {
						e.printStackTrace();
						throw new OBException(e.getMessage());
					} catch (SQLException e) {
						e.printStackTrace();
						throw new OBException(e.getMessage());
					}
					  
					  
				  }
			  } else if (doc_status_to.equalsIgnoreCase(reactive)) {
				  if (woAsset.isDisposed() || woAsset.isAssetmovement())
					  throw new OBException("Work Order "+woAsset.getWorkOrder().getIdentifier()
							  +" with asset "+woAsset.getAsset().getIdentifier()
							  +" is part of asset movement/disposal, then can not be reactivated");
			  }
		  }
		  
		  wo.setProcessed(true);
	  }
	  
	  OBDal.getInstance().commitAndClose();
	  
  }

  @Override
  public Boolean updateDocumentStatus(Entity entity, List<String> RecordId, String document_status_to,
      String column) {
    return super.updateDocumentStatus(entity, RecordId, document_status_to, column);
  }

  @Override
  public String getCoDocumentNo(String record, Tab tab) {
    // do nothing, cuma activate/deactivate pada kolom isactive
    return null;
  }

}
