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
 * @serial 201804130917
 */
public class MonthlyPaymentPointValidator implements GCardPoint{
   private GRider poRider;
   private GEntity poData;
   private double pnTranTotl;
   private long pnPntEarnx;
   private String psMessage;
   
   private boolean pbIsInLR = false;   
   private String psTransNox = "";
   
   public MonthlyPaymentPointValidator(){
      this.poRider = null;
      this.poData = null;
      this.pnTranTotl = 0;
      this.pnPntEarnx = 0;
      this.psMessage = "";
      
      pbIsInLR = false;   
      psTransNox = "";
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
   public void checkSource() {
      if(!checkLRSource()){
         if(this.psMessage.isEmpty()){
            checkORSource();
         }
      }
   }

   private boolean checkLRSource(){
        boolean bresult = false; 
        pbIsInLR = false;
        this.psTransNox = "";
        String branch = ((String)poData.getValue("sTransNox")).substring(0, 4);
        String date = SQLUtil.dateFormat((Date)poData.getValue("dTransact"), "yyyy-MM-dd");
        String refer = (String)poData.getValue("sSourceNo");      
      
        String lsSQL = "SELECT a.sTransNox, a.nAmountxx nTranAmtx, a.nRebatesx nDisCount, b.dFirstPay, DATE_ADD(DATE_ADD(b.dFirstPay, INTERVAL 5 MONTH ), INTERVAL 5 DAY) dLastXPay, b.nMonAmort, (b.nPaymTotl - a.nAmountxx) nPaymTotl, (b.nRebTotlx - a.nRebatesx) nRebTotlx, b.sSerialID" +
                        " FROM LR_Payment_Master a" +
                            ", MC_AR_Master b" +
                        " WHERE a.sTransNox LIKE " + SQLUtil.toSQL(branch + "%") +
                            " AND a.dTransact = " + SQLUtil.toSQL(date) + 
                            " AND a.sReferNox = " + SQLUtil.toSQL(refer) +
                            " AND a.sAcctNmbr = b.sAcctNmbr" + 
                            " AND a.cTranType = '2'" +
                            " AND a.cPostedxx = '2'" + 
                            " AND IFNULL(a.sCollIDxx, '') = ''";
      //                    " AND a.cGCrdPstd = " + SQLUtil.toSQL(used ? '1' : '0') +
      
        //mac 2020.08.22
        //  change the validation of a.cPostedxx <> '3' to a.cPostedxx = '2'
        //  as requested by audit thru ate she
        
        ResultSet loRS = poRider.executeQuery(lsSQL);
        System.out.println(lsSQL);
      
        if(loRS == null){
            System.out.println("Error loading source transaction...");
            this.psMessage = "Error loading source transaction...";
            this.pnTranTotl = 0;
            this.pnPntEarnx = 0;
        } else{
            try {
                System.out.println("Check if there are records...");
                if(loRS.next()){
                    System.out.println((String) poData.getValue("sGCardNox"));
                    //Check if amount paid is less than the monthly amortization...
                    if((loRS.getDouble("nTranAmtx") + loRS.getDouble("nDisCount")) < loRS.getDouble("nMonAmort")){
                        this.pnTranTotl = 0;
                        this.pnPntEarnx = 0;
                        psMessage = "Amount Paid is less than Monthly Amortization";
                        System.out.println("Amount Paid is less than Monthly Amortization");
                    }
                    //check if gcard is authorized to earn points from this account...
                    else if(validGCard(loRS.getString("sSerialID"), (String) poData.getValue("sGCardNox"))){
                        this.pnTranTotl = loRS.getDouble("nTranAmtx") + loRS.getDouble("nDisCount");
                        int lnTimes = (int)(pnTranTotl / loRS.getDouble("nMonAmort"));
                  
                        lsSQL = "SELECT nAmtPerPt, nMinPoint, nMaxPoint, nDiscount, nBonusPnt" +
                                " FROM G_Card_Points_Basis" +
                                " WHERE sSourceCd = " + SQLUtil.toSQL((String) poData.getValue("sSourceCd"));
                        ResultSet point = poRider.executeQuery(lsSQL);
                        System.out.println(lsSQL);
                  
                        if(point == null){
                            System.out.println("Error loading source transaction...");
                            this.psMessage = "Error loading source transaction...";
                            this.pnTranTotl = 0;
                            this.pnPntEarnx = 0;
                        } else{
                            if(point.next()){
                                this.psMessage = "";
                                //check if payment is more than 1 month amortization and transaction date is within 
                                //the first 6 months...
                                if(lnTimes >= 1 && loRS.getDate("dLastXPay").after((Date) poData.getValue("dTransact"))){
                                    int remain = 6 - (int)((loRS.getDouble("nPaymTotl") + loRS.getDouble("nRebTotlx")) / loRS.getDouble("nMonAmort"));
                                    //check if payment total is less than 6 amortization month
                                    if(remain > 0){
                                        if(lnTimes > remain){
                                            this.pnPntEarnx = (long)(remain * point.getDouble("nBonusPnt"));
                                        } else{
                                            this.pnPntEarnx = (long)(lnTimes * point.getDouble("nBonusPnt"));
                                        }
                                    }
                                }
                                //get the total point earn from this transaction
                                this.pnPntEarnx += (long)(lnTimes * point.getDouble("nMaxPoint"));
                                System.out.println("Points earn: " + Long.toString(this.pnPntEarnx));
                                System.out.println("No of times: " + Integer.toString(lnTimes));
                                this.pbIsInLR = true;
                                this.psTransNox = loRS.getString("sTransNox");
                                bresult = true;
                            } else {
                                System.out.println("Transaction type not found...");
                                this.psMessage = "Transaction type not found...";
                                this.pnPntEarnx = 0;
                            }
                        }
                    }
                    //gcard is not authorize to earn points from this account
                    else{
                        System.out.println("GCard is not authorized.");
                        psMessage = "GCard is not authorized.";
                        this.pnTranTotl = 0;
                        this.pnPntEarnx = 0;
                    }
                } else{
                    System.out.println("No record found...");
                    this.pnTranTotl = 0;
                    this.pnPntEarnx = 0;
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
                this.pnTranTotl = 0;
                this.pnPntEarnx = 0;
            }
        }
        return bresult;
    }

   private boolean checkORSource(){
      boolean bresult = false; 

      this.psTransNox = "";
      
      String branch = ((String)poData.getValue("sTransNox")).substring(0, 4);
      String date = SQLUtil.dateFormat((Date)poData.getValue("dTransact"), "yyyy-MM-dd");
      String refer = (String)poData.getValue("sSourceNo");      
      
      String lsSQL = "SELECT a.sTransNox, a.nTranAmtx, a.nDisCount, b.dFirstPay, DATE_ADD(DATE_ADD(b.dFirstPay, INTERVAL 5 MONTH ), INTERVAL 5 DAY) dLastXPay, b.nMonAmort, (b.nPaymTotl - a.nTranAmtx) nPaymTotl, (b.nRebTotlx - a.nDisCount) nRebTotlx, b.sSerialID" +
                    " FROM Receipt_Master a" +
                       " , MC_AR_Master b" +
                    " WHERE a.sTransNox LIKE " + SQLUtil.toSQL(branch + "%") +
                      " AND a.dTransact = " + SQLUtil.toSQL(date) + 
                      " AND a.sORNoxxxx = " + SQLUtil.toSQL(refer) + 
                      " AND a.sAcctNmbr = b.sAcctNmbr" + 
                      " AND a.cTranType = '1'" +
                      " AND (NOT (cTranStat & '3') = 3) AND (a.cTranStat & 4) = 0" +  
                      " AND a.sSourceCD <> 'CChk'";
      ResultSet loRS = poRider.executeQuery(lsSQL);
      System.out.println(lsSQL);
      
      if(loRS == null){
         this.psMessage = "Error loading source transaction...";
         this.pnTranTotl = 0;
         this.pnPntEarnx = 0;
      }
      else{
         try {
            if(loRS.next()){
               System.out.println((String) poData.getValue("sGCardNox"));
               //Check if amount paid is less than the monthly amortization...
               if((loRS.getDouble("nTranAmtx") + loRS.getDouble("nDisCount")) < loRS.getDouble("nMonAmort")){
                  this.pnTranTotl = 0;
                  this.pnPntEarnx = 0;
               }
               //check if gcard is authorized to earn points from this account...
               else if(validGCard(loRS.getString("sSerialID"), (String) poData.getValue("sGCardNox"))){
                  this.pnTranTotl = loRS.getDouble("nTranAmtx") + loRS.getDouble("nDisCount");
                  int lnTimes = (int)(pnTranTotl / loRS.getDouble("nMonAmort"));
                  
                  lsSQL = "SELECT nAmtPerPt, nMinPoint, nMaxPoint, nDiscount, nBonusPnt" +
                         " FROM G_Card_Points_Basis" +
                         " WHERE sSourceCd = " + SQLUtil.toSQL((String) poData.getValue("sSourceCd"));
                  ResultSet point = poRider.executeQuery(lsSQL);
                  System.out.println(lsSQL);

                  if(point == null){
                     this.psMessage = "Error loading source transaction...";
                     this.pnTranTotl = 0;
                     this.pnPntEarnx = 0;
                  }
                  else{
                     if(point.next()){
                        this.psMessage = "";
                        //check if payment is more than 1 month amortization and transaction date is within 
                        //the first 6 months...
                        if(lnTimes >= 1 && loRS.getDate("dLastXPay").after((Date) poData.getValue("dTransact"))){
                           int remain = 6 - (int)((loRS.getDouble("nPaymTotl") + loRS.getDouble("nRebTotlx")) / loRS.getDouble("nMonAmort"));
                           //check if payment total is less than 6 amortization month
                           if(remain > 0){
                              if(lnTimes > remain){
                                 this.pnPntEarnx = (long)(remain * point.getDouble("nBonusPnt"));
                              }
                              else{
                                 this.pnPntEarnx = (long)(lnTimes * point.getDouble("nBonusPnt"));
                              }
                           }
                        }
                        
                        //get the total point earn from this transaction
                        this.pnPntEarnx += (long)(lnTimes * point.getDouble("nMaxPoint"));
                        this.psTransNox = loRS.getString("sTransNox");
                        bresult = true;
                     }
                     else{
                        this.psMessage = "Transaction type not found...";
                        this.pnPntEarnx = 0;
                     }
                  }
               }
               //gcard is not authorize to earn points from this account
               else{
                  this.pnTranTotl = 0;
                  this.pnPntEarnx = 0;
               }
            }
            else{
               this.pnTranTotl = 0;
               this.pnPntEarnx = 0;
            }
         } catch (SQLException ex) {
            ex.printStackTrace();
            this.pnTranTotl = 0;
            this.pnPntEarnx = 0;
         }
      }
      
      return bresult;
   }
   
   //kalyptus - 2016.04.19 02:00pm
   //validates transaction if account belongs to the GCard
   private boolean validGCard(String fsSerialID, String fsGCardNox){
      String lsSQL = "SELECT sGCardNox" + 
                    " FROM MC_Serial_Service" + 
                    " WHERE sSerialID = " + SQLUtil.toSQL(fsSerialID);
      
      ResultSet loRS = poRider.executeQuery(lsSQL);
      System.out.println(lsSQL);
      
      boolean isValid = false;
      try {
         while(loRS.next()){
            if(fsGCardNox.equalsIgnoreCase(loRS.getString("sGCardNox"))){
               isValid = true;
            }
         }
      } catch (SQLException ex) {
         ex.printStackTrace();
         isValid = false;
      }
      
      return isValid;
   }
   
   @Override
   public boolean SaveOthers() {
      String lsSQL;
      if(this.psTransNox.length() != 0){
         if(!pbIsInLR)
            lsSQL = "UPDATE Receipt_Master SET" +
                           "  cTranStat = cTranStat ^ 4"  +
                       " WHERE sTransNox LIKE " + SQLUtil.toSQL(this.psTransNox);
         else
             lsSQL = "UPDATE LR_Payment_Master" + 
                        " SET cGCrdPstd = '1'" + 
                       " WHERE sTransNox LIKE " + SQLUtil.toSQL(this.psTransNox);

         poRider.executeQuery(lsSQL, pbIsInLR ? "LR_Payment_Master" : "Receipt_Master", "", "");   
         //check for possible error in executing this query...
         if(poRider.getErrMsg().length() > 0){
            this.psMessage = "SaveOthers: " + poRider.getErrMsg();
            return false;
         }
         else
            return true;
      }
      
      return false;
   }
   @Override
   public boolean CancelOthers() {
      String lsSQL;
      if(this.psTransNox.length() != 0){
         if(!pbIsInLR)
            lsSQL = "UPDATE Receipt_Master SET" +
                           "  cTranStat = cTranStat ^ 4"  +
                       " WHERE sTransNox LIKE " + SQLUtil.toSQL(this.psTransNox);
         else
             lsSQL = "UPDATE LR_Payment_Master" + 
                        " SET cGCrdPstd = '0'" + 
                       " WHERE sTransNox LIKE " + SQLUtil.toSQL(this.psTransNox);

         poRider.executeQuery(lsSQL, pbIsInLR ? "LR_Payment_Master" : "Receipt_Master", "", "");   
         if(poRider.getErrMsg().length() == 0)
            return true;
      }
      
      return false;
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
      return psMessage;
   }
    @Override
    public double getTotalAmount() {
        return this.pnTranTotl;
    }
   
}
