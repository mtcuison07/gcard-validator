/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.rmj.gcard.validator.point;

/**
 *
 * @author kalyptus - 2019.03.20 01:56pm
 */
public class GCardPointValidatorFactory {
    public enum PointType {
        JOB_ORDER, MC_SALES, MC_SALES_REFERRAL, MONTHLY_PAYMENT, SP_SALES
    }    
    
    public static GCardPoint make(GCardPointValidatorFactory.PointType point){
        switch (point) {
        case JOB_ORDER:
            return new JobOrderPointValidator();
        case MC_SALES:
            return new MCSalesPointValidator();
        case MC_SALES_REFERRAL:
            return new MCSalesReferralPointValidator();
        case MONTHLY_PAYMENT:
            return new MonthlyPaymentPointValidator();
        case SP_SALES:
            return new SPSalesPointValidator();
        default:
            return null;
        }
    }
}
