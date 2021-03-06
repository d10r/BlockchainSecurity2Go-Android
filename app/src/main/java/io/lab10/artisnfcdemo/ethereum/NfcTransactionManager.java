package io.lab10.artisnfcdemo.ethereum;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.nfc.tech.IsoDep;
import android.util.Log;
import android.util.Pair;
import android.widget.TextView;

import io.lab10.AppConstants;
import io.lab10.artisnfcdemo.R;
import io.lab10.artisnfcdemo.VotingActivity;
import io.lab10.artisnfcdemo.ethereum.utils.EthereumUtils;
import io.lab10.artisnfcdemo.infineon.apdu.response.GenerateSignatureResponseApdu;
import io.lab10.artisnfcdemo.infineon.exceptions.NfcCardException;
import io.lab10.artisnfcdemo.utils.UiUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.TransactionManager;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import static io.lab10.AppConstants.*;
import static io.lab10.artisnfcdemo.utils.UiUtils.showToast;

/**
 * Extends web3j Transaction manager, to create ETH transactions using
 * the Infineon card for signing.
 */
public class NfcTransactionManager extends TransactionManager {

    private IsoDep tag;
    private String publicKey;
    private String fromAddress;
    private Activity activity;

    /**
     * Create Nfc Transaction manager.
     *
     * @param web3j
     * @param fromAddress
     * @param tag
     * @param publicKey
     */
    public NfcTransactionManager(Web3j web3j, String fromAddress, IsoDep tag, String publicKey, Activity activity) {
        super(web3j, fromAddress);
        this.fromAddress = fromAddress;
        this.tag = tag;
        this.publicKey = publicKey;
        this.activity = activity;
    }

    /**
     * this method will be called on transaction sending
     *
     * @param gasPrice
     * @param gasLimit
     * @param to
     * @param data
     * @param value
     * @return ether object with transaction hash
     */
    @Override
    public EthSendTransaction sendTransaction(
            BigInteger gasPrice, BigInteger gasLimit, String to,
            String data, BigInteger value) {

        SharedPreferences pref = activity.getSharedPreferences(PREFERENCE_FILENAME, Context.MODE_PRIVATE);
        byte chainId = AppConstants.ARTIS_SIGMA1_CHAIN_ID;
        if (!pref.getBoolean(PREF_KEY_MAIN_NETWORK, true)) {
            chainId = AppConstants.ARTIS_TAU1_CHAIN_ID;
        }

        Pair<EthSendTransaction, GenerateSignatureResponseApdu> response = null;
        try {
            Log.d(TAG, "sending ETH transaction..");
            response = EthereumUtils.sendTransaction(gasPrice, gasLimit, fromAddress, to, value, tag, publicKey, data,
                    UiUtils.getFullNodeUrl(activity), chainId, pref.getInt(KEY_INDEX_OF_CARD, 1),
                    ((TextView) activity.findViewById(R.id.pin)).getText().toString().getBytes(StandardCharsets.UTF_8));
            Log.d(TAG, String.format("sending ETH transaction finished with Hash: %s", response.first.getTransactionHash()));
            if (activity != null) {
                if ("Voting".equals(activity.getTitle().toString())) {
                    showToast(activity.getString(R.string.voted_successful), activity);
                } else {
                    showToast(activity.getString(R.string.send_success), activity);
                }
            }

            return response.first;
        } catch (IOException e) {
            showToast(activity.getString(R.string.lost_tag), activity);
            Log.e(TAG, "IOException while voting, lost tag", e);
        } catch (NfcCardException e) {
            if (activity != null) {
                showToast(e.getMessage(), activity);
                Log.e(TAG, "Exception while sending ether transaction", e);
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception while sending ether transaction", e);
            if (!(activity instanceof VotingActivity)) {
                showToast(String.format(activity.getString(R.string.could_not_send_transaction), e.getMessage()), activity);
            }
        } finally {
            if (response != null && (response.second.getGlobalSigCounterAsInteger() < WARNING_SIG_COUNTER ||
                    response.second.getSigCounterAsInteger() < WARNING_SIG_COUNTER)) {
                showToast("Signature counter below " + WARNING_SIG_COUNTER + "! Backup your funds!", activity);
            }
        }
        return null;
    }
}
