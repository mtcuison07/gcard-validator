/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.rmj.gcard.validator.point;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.rmj.appdriver.GRider;
import org.rmj.appdriver.iface.GEntity;
import org.rmj.appdriver.SQLUtil;

/**
 * @author kalyptus
 * @serial 201804130917
 */
public class JobOrderPointValidator implements GCardPoint{
   private GRider poRider;
   private GEntity poData;
   private double pnTranTotl;
   private long pnPntEarnx;
   private String psMessage;

   public JobOrderPointValidator(){
      this.poRider = null;
      this.poData = null;
      this.pnTranTotl = 0;
      this.pnPntEarnx = 0;
      this.psMessage = "";
   }
   
   @Override
   public void setGRider(GRider poRider) {
      this.poRider = poRider;
   }

   @Override
   public void setData(GEntity poData) {
      this.poData = poData;
   }

   //Note: the primary reference should be from sp_so_master;
   @Override
   public void checkSource() {
      if(!checkSISource()){
         if(this.psMessage.isEmpty()){
            checkORSource();
         }
      }
   }

   private boolean checkORSource(){
      boolean bresult = false; 
      String branch = ((String)poData.getValue("sTransNox")).substring(0, 4);
      String date = SQLUtil.dateFormat((Date)poData.getValue("dTransact"), "yyyy-MM-dd");
      String refer = (String)poData.getValue("sSourceNo");
      
      //check if reference is from receipt
      String lsSQL = "SELECT sReferNox, nTranTotl" + 
                    " FROM Receipt_Master" + 
                    " WHERE sTransNox LIKE " + SQLUtil.toSQL(branch + "%") +
                      " AND dTransact = " + SQLUtil.toSQL(date) + 
                      " AND sORNoxxxx = " + SQLUtil.toSQL(refer) + 
                      " AND sSourceCD = 'SPJO'" +
                      " AND cTranStat <> '3'";
      ResultSet loRS = poRider.executeQuery(lsSQL);
      System.out.println("checkORSource: " + lsSQL);
      if(loRS == null){
         this.psMessage = "checkORSource: Error loading receipt source transaction...";
         this.pnTranTotl = 0;
         this.pnPntEarnx = 0;
      }
      else{
         try {
            if(loRS.next()){
               System.out.println("checkORSource: Record found in OR.");
               //kung may nakita sa joborder then icheck natin ang trantotal ng joborder...
               lsSQL = "SELECT nTranTotl FROM JobOrderBranch_Master WHERE sTransNox = " + SQLUtil.toSQL( loRS.getString("sReferNox")) + 
                " UNION SELECT nTranTotl FROM JobOrder_Master WHERE sTransNox = " + SQLUtil.toSQL( loRS.getString("sReferNox"));      
               ResultSet loJo = poRider.executeQuery(lsSQL);
               System.out.println("checkORSource: " + lsSQL);
               if(loJo == null){
                  this.psMessage = "checkORSource: Error loading job order transaction...";
                  this.pnTranTotl = 0;
                  this.pnPntEarnx = 0;
               }
               else{
                  if(loJo.next()){
                     if(loJo.getDouble("nTranTotl") != loRS.getDouble("nTranTotl")){
                        this.psMessage = "checkORSource: Please use the Sales Invoice No if job order has SI...";
                        this.pnTranTotl = 0;
                        this.pnPntEarnx = 0;
                        return false;
                     }
                     
                     this.pnTranTotl = loRS.getDouble("nTranTotl");
                     lsSQL = "SELECT nAmtPerPt, nMinPoint, nMaxPoint, nDiscount, nBonusPnt" +
                            " FROM G_Card_Points_Basis" +
                            " WHERE sSourceCd = " + SQLUtil.toSQL((String) poData.getValue("sSourceCd"));
                     ResultSet point = poRider.executeQuery(lsSQL);
                     System.out.println("checkORSource: " + lsSQL);

                     if(point == null){
                        this.psMessage = "checkORSource: Error loading source transaction...";
                        this.pnTranTotl = 0;
                        this.pnPntEarnx = 0;
                     }
                     else{
                        if(point.next()){
                           this.psMessage = "";
                           this.pnPntEarnx = (long)(this.pnTranTotl / point.getDouble("nAmtPerPt"));
                           bresult = true;
                        }
                        else{
                           this.psMessage = "checkORSource: Transaction type not found...";
                           this.pnPntEarnx = 0;
                        }
                     }
                  }
                  else{
                     this.psMessage = "checkORSource: Job Order transaction not found...";
                     this.pnTranTotl = 0;
                     this.pnPntEarnx = 0;
                  }
               }
            }
         } catch (SQLException ex) {
            ex.printStackTrace();
            this.psMessage = "checkORSource: Error acessing field...";
            this.pnTranTotl = 0;
            this.pnPntEarnx = 0;
         }
      }
      
      return bresult;
      
   }

   private boolean checkSISource(){
      boolean bresult = false; 
      String branch = ((String)poData.getValue("sTransNox")).substring(0, 4);
      String date = SQLUtil.dateFormat((Date)poData.getValue("dTransact"), "yyyy-MM-dd");
      String refer = (String)poData.getValue("sSourceNo");
      
      //check if reference is from SO Master
      String lsSQL = "SELECT sReferNox, nTranTotl, nAmtPaidx" + 
                    " FROM SP_SO_Master" + 
                    " WHERE sTransNox LIKE " + SQLUtil.toSQL(branch + "%") +
                      " AND dTransact = " + SQLUtil.toSQL(date) + 
                      " AND sSalesInv = " + SQLUtil.toSQL(refer) + 
                      " AND sSourceCD = 'SCJO'" +
                      " AND cTranStat <> '3'";
      System.out.println("checkSISource: " + lsSQL);
      ResultSet loRS = poRider.executeQuery(lsSQL);
      
      if(loRS == null){
         this.psMessage = "checkSISource: Error loading SP Sales source transaction...";
         this.pnTranTotl = 0;
         this.pnPntEarnx = 0;
      }
      else{
         try {
            if(loRS.next()){
               //kung iverify kung okey yung job order...
               lsSQL = "SELECT nTranTotl FROM JobOrderBranch_Master WHERE sTransNox = " + SQLUtil.toSQL( loRS.getString("sReferNox")) + 
                " UNION SELECT nTranTotl FROM JobOrder_Master WHERE sTransNox = " + SQLUtil.toSQL( loRS.getString("sReferNox"));      
               ResultSet loJo = poRider.executeQuery(lsSQL);
               if(loJo == null){
                  this.psMessage = "checkSISource: Error loading job order transaction...";
                  this.pnTranTotl = 0;
                  this.pnPntEarnx = 0;
               }
               else{
                  if(loJo.next()){
                     //this.pnTranTotl = loRS.getDouble("nAmtPaidx");
                     this.pnTranTotl = loJo.getDouble("nTranTotl");
                     lsSQL = "SELECT nAmtPerPt, nMinPoint, nMaxPoint, nDiscount, nBonusPnt" +
                            " FROM G_Card_Points_Basis" +
                            " WHERE sSourceCd = " + SQLUtil.toSQL((String) poData.getValue("sSourceCd"));
                     ResultSet point = poRider.executeQuery(lsSQL);

                     if(point == null){
                        this.psMessage = "checkSISource: Error loading source transaction...";
                        this.pnTranTotl = 0;
                        this.pnPntEarnx = 0;
                     }
                     else{
                        if(point.next()){
                           this.psMessage = "";
                           this.pnPntEarnx = (long)(this.pnTranTotl / point.getDouble("nAmtPerPt"));
                           bresult = true;
                        }
                        else{
                           this.psMessage = "checkSISource: Transaction type not found...";
                           this.pnPntEarnx = 0;
                        }
                     }
                  }
                  else{
                     this.psMessage = "checkSISource: Job Order transaction not found...";
                     this.pnTranTotl = 0;
                     this.pnPntEarnx = 0;
                  }
               }
            }
         } catch (SQLException ex) {
            ex.printStackTrace();
            this.psMessage = "checkSISource: Error acessing field...";
            this.pnTranTotl = 0;
            this.pnPntEarnx = 0;
         }
      }
      
      return bresult;
      
   }
   
   @Override
   public boolean SaveOthers() {
      return true;
   }

   @Override
   public boolean CancelOthers() {
      return true;
   }

   @Override
   public boolean isSaveValid() {
      return true;
   }

   @Override
   public boolean isCancelValid() {
      return true;
   }

   @Override
   public long getPoints() {
      return this.pnPntEarnx;
   }

   @Override
   public String getMessage() {
      return this.psMessage;
   }

    @Override
    public double getTotalAmount() {
        return this.pnTranTotl;
    }
}
