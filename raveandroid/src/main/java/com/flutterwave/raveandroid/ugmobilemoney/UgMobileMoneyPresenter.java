package com.flutterwave.raveandroid.ugmobilemoney;

import android.content.Context;
import android.util.Log;

import com.flutterwave.raveandroid.FeeCheckRequestBody;
import com.flutterwave.raveandroid.Payload;
import com.flutterwave.raveandroid.PayloadBuilder;
import com.flutterwave.raveandroid.R;
import com.flutterwave.raveandroid.RaveConstants;
import com.flutterwave.raveandroid.RavePayInitializer;
import com.flutterwave.raveandroid.Utils;
import com.flutterwave.raveandroid.ViewObject;
import com.flutterwave.raveandroid.card.ChargeRequestBody;
import com.flutterwave.raveandroid.data.Callbacks;
import com.flutterwave.raveandroid.data.NetworkRequestImpl;
import com.flutterwave.raveandroid.data.RequeryRequestBody;
import com.flutterwave.raveandroid.responses.FeeCheckResponse;
import com.flutterwave.raveandroid.responses.GhChargeResponse;
import com.flutterwave.raveandroid.responses.RequeryResponse;
import com.flutterwave.raveandroid.validators.AmountValidator;

import java.util.HashMap;

/**
 * Created by Jeremiah on 10/12/2018.
 */

public class UgMobileMoneyPresenter implements UgMobileMoneyContract.UserActionsListener {
    private Context context;
    private UgMobileMoneyContract.View mView;
    private AmountValidator amountValidator = new AmountValidator();

     UgMobileMoneyPresenter(Context context, UgMobileMoneyContract.View mView) {
        this.context = context;
        this.mView = mView;
    }

    @Override
    public void fetchFee(final Payload payload) {
        FeeCheckRequestBody body = new FeeCheckRequestBody();
        body.setAmount(payload.getAmount());
        body.setCurrency(payload.getCurrency());
        body.setPtype("3");
        body.setPBFPubKey(payload.getPBFPubKey());

        mView.showProgressIndicator(true);

        new NetworkRequestImpl().getFee(body, new Callbacks.OnGetFeeRequestComplete() {
            @Override
            public void onSuccess(FeeCheckResponse response) {
                mView.showProgressIndicator(false);

                try {
                    mView.displayFee(response.getData().getCharge_amount(), payload);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    mView.showFetchFeeFailed(RaveConstants.transactionError);
                }
            }

            @Override
            public void onError(String message) {
                mView.showProgressIndicator(false);
                Log.e(RaveConstants.RAVEPAY, message);
                mView.showFetchFeeFailed(RaveConstants.transactionError);
            }
        });
    }

    @Override
    public void chargeUgMobileMoney(final Payload payload, final String encryptionKey) {
        String cardRequestBodyAsString = Utils.convertChargeRequestPayloadToJson(payload);
        String encryptedCardRequestBody = Utils.getEncryptedData(cardRequestBodyAsString, encryptionKey).trim().replaceAll("\\n", "");

        ChargeRequestBody body = new ChargeRequestBody();
        body.setAlg("3DES-24");
        body.setPBFPubKey(payload.getPBFPubKey());
        body.setClient(encryptedCardRequestBody);

        mView.showProgressIndicator(true);

        new NetworkRequestImpl().chargeGhanaMobileMoneyWallet(body, new Callbacks.OnGhanaChargeRequestComplete() {
            @Override
            public void onSuccess(GhChargeResponse response, String responseAsJSONString) {

                mView.showProgressIndicator(false);

                if (response.getData() != null) {
                    Log.d("resp", responseAsJSONString);

                    String flwRef = response.getData().getFlwRef();
                    String txRef = response.getData().getTx_ref();
                    requeryTx(flwRef, txRef, payload.getPBFPubKey());
                }
                else {
                    mView.onPaymentError(RaveConstants.noResponse);
                }

            }

            @Override
            public void onError(String message, String responseAsJSONString) {
                mView.showProgressIndicator(false);
                mView.onPaymentError(message);
            }
        });
    }

    @Override
    public void requeryTx(final String flwRef, final String txRef, final String publicKey) {

        RequeryRequestBody body = new RequeryRequestBody();
        body.setFlw_ref(flwRef);
        body.setPBFPubKey(publicKey);

        mView.showPollingIndicator(true);

        new NetworkRequestImpl().requeryTx(body, new Callbacks.OnRequeryRequestComplete() {
            @Override
            public void onSuccess(RequeryResponse response, String responseAsJSONString) {
                if (response.getData() == null) {
                    mView.onPaymentFailed(response.getStatus(), responseAsJSONString);
                }
                else if (response.getData().getChargeResponseCode().equals("02")){
//                    Log.d("Requery response",responseAsJSONString);
                    mView.onPollingRoundComplete(flwRef, txRef, publicKey);
                }
                else if (response.getData().getChargeResponseCode().equals("00")) {
                    mView.showPollingIndicator(false);
                    mView.onPaymentSuccessful(flwRef, txRef, responseAsJSONString);
                }
                else {
                    mView.showProgressIndicator(false);
                    mView.onPaymentFailed(response.getData().getStatus(), responseAsJSONString);
                }
            }

            @Override
            public void onError(String message, String responseAsJSONString) {
                mView.onPaymentFailed(message, responseAsJSONString);
            }
        });
    }

    @Override
    public void onDataCollected(HashMap<String, ViewObject> dataHashMap) {

         boolean valid = true;

        int amountID = dataHashMap.get(RaveConstants.fieldAmount).getViewId();
        String amount = dataHashMap.get(RaveConstants.fieldAmount).getData();
        Class amountViewType = dataHashMap.get(RaveConstants.fieldAmount).getViewType();

        int phoneID = dataHashMap.get(RaveConstants.fieldPhone).getViewId();
        String phone = dataHashMap.get(RaveConstants.fieldPhone).getData();
        Class phoneViewType = dataHashMap.get(RaveConstants.fieldPhone).getViewType();

        if (!amountValidator.isAmountValid(amount)) {
            valid = false;
            mView.showFieldError(amountID, RaveConstants.validAmountPrompt, amountViewType);
        }

         if (phone.length() < 1) {
                valid = false;
                mView.showFieldError(phoneID, RaveConstants.validPhonePrompt, phoneViewType);
        }

         if (valid) {
             mView.onValidationSuccessful(dataHashMap);
         }

    }

    @Override
    public void processTransaction(HashMap<String, ViewObject> dataHashMap, RavePayInitializer ravePayInitializer) {

        if (ravePayInitializer!=null) {

            PayloadBuilder builder = new PayloadBuilder();
            builder.setAmount(ravePayInitializer.getAmount() + "")
//                    .setCountry(ravePayInitializer.getCountry())
                    .setCountry("NG") //Country has to be set to NG for UGX payments (as at 10/12/2018)
                    .setCurrency(ravePayInitializer.getCurrency())
                    .setEmail(ravePayInitializer.getEmail())
                    .setFirstname(ravePayInitializer.getfName())
                    .setLastname(ravePayInitializer.getlName())
                    .setIP(Utils.getDeviceImei(context))
                    .setTxRef(ravePayInitializer.getTxRef())
                    .setMeta(ravePayInitializer.getMeta())
                    .setSubAccount(ravePayInitializer.getSubAccount())
                    .setNetwork("UGX")
                    .setPhonenumber(dataHashMap.get(RaveConstants.fieldPhone).getData())
                    .setPBFPubKey(ravePayInitializer.getPublicKey())
                    .setIsPreAuth(ravePayInitializer.getIsPreAuth())
                    .setDevice_fingerprint(Utils.getDeviceImei(context));

            if (ravePayInitializer.getPayment_plan() != null) {
                builder.setPaymentPlan(ravePayInitializer.getPayment_plan());
            }

            Payload body = builder.createUgMobileMoneyPayload();

            if (ravePayInitializer.getIsDisplayFee()) {
                fetchFee(body);
            } else {
                chargeUgMobileMoney(body, ravePayInitializer.getEncryptionKey());
            }
        }
    }

    @Override
    public void init(RavePayInitializer ravePayInitializer) {

        if (ravePayInitializer != null) {

            Boolean isAmountValid = amountValidator.isAmountValid(ravePayInitializer.getAmount());
            if (isAmountValid) {
                mView.onAmountValidationSuccessful(String.valueOf(ravePayInitializer.getAmount()));
            }
        }
    }
}


