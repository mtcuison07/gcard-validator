/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.rmj.gcard.validator.point;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import org.rmj.appdriver.GRider;
import org.rmj.appdriver.iface.GEntity;
import org.rmj.appdriver.SQLUtil;

/**
 * @author kalyptus
 * @serial 201804131136
 */
public class MCSalesReferralPointValidator implements GCardPoint{
   private GRider poRider;
   private GEntity poData;
   private long pnTranTotl;
   private long pnPntEarnx;
   private String psMessage;
   private String psTransNo;
   private String psAgentID;
   
   public MCSalesReferralPointValidator(){
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
   
   @Override
   public void checkSource(){
      String branch = ((String)poData.getValue("sTransNox")).substring(0, 4);
      String date = SQLUtil.dateFormat((Date)poData.getValue("dTransact"), "yyyy-MM-dd");
      String refer = (String)poData.getValue("sSourceNo");
      
      String lsSQL = "SELECT c.sAgentIDx, b.sTransNox, b.sSerialID, b.sReplMCID, IFNULL(c.cGCRefrlx, 'X') cGCRefrlx, IFNULL(c.cReleased, 'X') cReleased" +  
                    " FROM MC_SO_Master a" +
                        ", MC_SO_Detail b" +
                           " LEFT JOIN MC_SO_Agent c ON b.sTransNox = c.sTransNox" + 
                    " WHERE a.sTransNox = b.sTransNox" +
                      " AND a.sTransNox LIKE " + SQLUtil.toSQL(branch + "%") +
                      " AND a.dTransact = " + SQLUtil.toSQL(date) + 
                      " AND a.sDRNoxxxx = " + SQLUtil.toSQL(refer) + 
                      " AND a.cTranStat <> '3'" +
                      " AND b.sSerialID <> ''" +
                      " AND b.cMotorNew = '1'";
      ResultSet loRS = poRider.executeQuery(lsSQL);
      System.out.println(lsSQL);
      this.psTransNo = "";
      this.psAgentID = "";
      if(loRS == null){
         this.psMessage = "checkSource: Error loading MC Sales transaction...";
         this.pnTranTotl = 0;
         this.pnPntEarnx = 0;         
      }
      else{
         try {
            this.pnTranTotl = 0;
            while(loRS.next()){
               //Do not allow replacement to earn points...
               //If so how will this transaction get an FSEP for the MC?
               if(loRS.getString("sReplMCID").length() != 0){
                  this.psMessage = "checkSource: MC Sales is a replacement...";
                  this.pnTranTotl = 0;
                  this.pnPntEarnx = 0;    
                  this.psTransNo = "";
                  break;
               }
               else if(loRS.getString("cGCRefrlx").compareToIgnoreCase("X") == 0){
                  this.psMessage = "checkSource: MC Sales has no agent...";
                  this.pnTranTotl = 0;
                  this.pnPntEarnx = 0;    
                  this.psTransNo = "";
                  break;
               }
               else if(loRS.getString("cGCRefrlx").compareToIgnoreCase("0") == 0){
                  this.psMessage = "checkSource: MC Sales agent is not for GCard...";
                  this.pnTranTotl = 0;
                  this.pnPntEarnx = 0;    
                  this.psTransNo = "";
                  break;
               }
               else if(loRS.getString("cReleased").compareToIgnoreCase("1") == 0){
                  this.psMessage = "checkSource: MC Sales agent referral was released...";
                  this.pnTranTotl = 0;
                  this.pnPntEarnx = 0;    
                  this.psTransNo = "";
                  break;
               }
               else{
                  this.pnTranTotl += 1;
                  this.psTransNo = loRS.getString("sTransNox");
                  this.psAgentID = loRS.getString("sAgentIDx");
               }
            } //while(loRS.next()){
            
            //If no error was detected from validating the MC Sales then set points here...
            if(this.pnTranTotl > 0){
               lsSQL = "SELECT nAmtPerPt, nMinPoint, nMaxPoint, nDiscount, nBonusPnt" +
                      " FROM G_Card_Points_Basis" +
                      " WHERE sSourceCd = " + SQLUtil.toSQL((String) poData.getValue("sSourceCd"));
               System.out.append(lsSQL);
               ResultSet point = poRider.executeQuery(lsSQL);

               if(point == null){
                  this.psMessage = "checkSource: Error loading source transaction...";
                  this.pnTranTotl = 0;
                  this.pnPntEarnx = 0;
                  this.psTransNo = "";
               }
               else{
                  if(point.next()){
                     this.pnPntEarnx = (long)(pnTranTotl * point.getDouble("nMaxPoint"));
                  }
                  else{
                     this.psMessage = "checkSource: Points basis not found...";
                     this.pnPntEarnx = 0;
                     this.psTransNo = "";
                  }
               }
            }
         } catch (SQLException ex) {
            ex.printStackTrace();
            this.pnTranTotl = 0;
            this.pnPntEarnx = 0;         
            this.psTransNo = "";
         } //try {
      }      
   }

   @Override
   public boolean SaveOthers() {
      try {
         String card = (String) (poData.getValue("sGCardNox"));
         String lsSQL = "SELECT sClientID" +
                 " FROM G_Card_Master" +
                 " WHERE sGCardNox = " + SQLUtil.toSQL(card);  
         ResultSet loRS = poRider.executeQuery(lsSQL);
         
         if(loRS == null){
            this.psMessage = "SaveOthers: Error loading GCard owner info...";
            return false;
         }
         
         if(!loRS.next()){
            this.psMessage = "SaveOthers: Unable to find GCard owner info...";
            return false;
         }
         
         //Agent ID != sClientID
         if(this.psAgentID.compareToIgnoreCase(loRS.getString("sClientID")) != 0){
            this.psMessage = "SaveOthers: Agent is different from GCard owner info...";
            return false;
         }
         
         lsSQL = "UPDATE MC_SO_Agent" + 
                " SET nCommAmtx = " + this.pnPntEarnx + 
                   ", sReleasNo = " + SQLUtil.toSQL((String) poData.getValue("sTransNox")) + 
                   ", sReferNox = " + SQLUtil.toSQL((String) poData.getValue("sTransNox")) + 
                   ", cReleased = '1'" + 
                   ", sReleased = " + SQLUtil.toSQL(poRider.getUserID()) +
                   ", dReleased = " + SQLUtil.toSQL(SQLUtil.dateFormat((Date)poData.getValue("dTransact"), "yyyy-MM-dd")) + 
                " WHERE sTransNox = " +  SQLUtil.toSQL(this.psTransNo);
         poRider.executeQuery(lsSQL, "MC_SO_Agent", "", "");
         
         //check for possible error in executing this query...
         if(poRider.getErrMsg().length() > 0){
            this.psMessage = "SaveOthers: Error updating the MC_SO_Agent record...";
            return false;
         }
         
         return true;
      } catch (SQLException ex) {
         ex.printStackTrace();
         return false;
      }
   }

   @Override
   public boolean CancelOthers() {
      String lsSQL;
      lsSQL = "UPDATE MC_SO_Agent" + 
             " SET nCommAmtx = 0" + 
                ", sReleasNo = NULL" + 
                ", sReferNox = ''" + 
                ", cReleased = '0'" + 
                ", sReleased = ''" +
                ", dReleased = NULL" + 
             " WHERE sTransNox = " +  SQLUtil.toSQL(this.psTransNo);
      poRider.executeQuery(lsSQL, "MC_SO_Agent", "", "");

      //check for possible error in executing this query...
      if(poRider.getErrMsg().length() > 0){
         this.psMessage = "CancelOthers: Error cancelling the MC_SO_Agent record...";
         return false;
      }

      return true;
   }

   @Override
   public boolean isSaveValid() {
      return true;
   }

   @Override
   public boolean isCancelValid() {
      System.out.println("Checking Cancel Validation " + this.toString());
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
