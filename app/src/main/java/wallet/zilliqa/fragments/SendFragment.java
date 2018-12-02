package wallet.zilliqa.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.OnClick;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import wallet.zilliqa.BaseApplication;
import wallet.zilliqa.BaseFragment;
import wallet.zilliqa.BuildConfig;
import wallet.zilliqa.Constants;
import wallet.zilliqa.R;
import wallet.zilliqa.data.local.AppDatabase;
import wallet.zilliqa.data.local.PreferencesHelper;
import wallet.zilliqa.dialogs.ConfirmPaymentDialog;
import wallet.zilliqa.qrscanner.QRScannerActivity;
import wallet.zilliqa.utils.Cryptography;
import wallet.zilliqa.utils.DialogFactory;

public class SendFragment extends BaseFragment {

  @BindView(R.id.send_editText_to) EditText send_editText_to;
  @BindView(R.id.send_editText_amount) EditText send_editText_amount;
  @BindView(R.id.send_button_send) Button send_button_send;
  @BindView(R.id.send_imageButton_scanqr) ImageView send_imageButton_scanqr;
  @BindView(R.id.seekBar_fee) SeekBar seekBar_fee;

  @BindView(R.id.send_textView_amount) TextView send_textView_amount;
  @BindView(R.id.send_textView_currency) TextView send_textView_currency;
  @BindView(R.id.send_textView_fee) TextView send_textView_fee;
  Disposable disposable;
  private BigDecimal balanceZIL;
  private BigInteger feeZIL;
  private PreferencesHelper preferencesHelper;
  private AppDatabase db;

  public SendFragment() {
  }

  public static SendFragment newInstance(boolean isjustcreated) {
    Bundle args = new Bundle();
    args.putBoolean("isjustcreated", isjustcreated);
    SendFragment fragment = new SendFragment();
    fragment.setArguments(args);
    fragment.setRetainInstance(true);
    return fragment;
  }

  @Nullable @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_send, container, false);
  }

  @Override public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    preferencesHelper = BaseApplication.getPreferencesHelper(getActivity());
    db = BaseApplication.getAppDatabase(getActivity());

    //TODO: remove me
    if (BuildConfig.DEBUG) {
      send_editText_to.setText(
          Constants.TESTADDRESS);
      send_editText_amount.setText("0.1");
    }

    //send_textView_fee.setText(
    //    String.format("Fee (~): %s ZIL",
    //        Convert.fromWei(feeToken.toString(), Convert.Unit.ETHER).toString()));

    send_button_send.setClickable(false);
  }

  @Override public void onResume() {
    super.onResume();

    if (!Constants.lastScanAddress.isEmpty()) {
      send_editText_to.setText(Constants.lastScanAddress);
    }

    disposable = Observable.interval(100, 15000,
        TimeUnit.MILLISECONDS)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this::updateBalances);
  }

  @Override public void onPause() {
    super.onPause();
    disposable.dispose();
  }

  @OnClick(R.id.send_button_send) public void onClickSend() {
    double amount_to_send = 0;
    if (send_editText_amount.getText().toString().trim().length() > 0) {
      try {
        //TODO: transform this to BigDecimal
        amount_to_send = Double.valueOf(send_editText_amount.getText().toString().trim());
      } catch (Exception ignored) {
      }
    }
    if (amount_to_send > 9999999) {
      DialogFactory.warning_toast(getActivity(),
          "This app doesn't believe that you have so much ETH/Tokens so it blocks this call")
          .show();
      return;
    }

    if (send_editText_to.getText().toString().length() < 30) {
      DialogFactory.warning_toast(getActivity(), "You need to enter the destination address.")
          .show();
      return;
    }

    if (amount_to_send <= 0) {
      DialogFactory.warning_toast(getActivity(), "Please enter the amount you want to send").show();
      return;
    }

    if (balanceZIL.compareTo(new BigDecimal(amount_to_send)) < 0) {
      DialogFactory.warning_toast(getActivity(),
          "Seems you don't have enough ZIL for this transaction.").show();
      send_textView_amount.setTextColor(getResources().getColor(R.color.material_red));
      return;
    }
    sendTheMoney(true, send_editText_to.getText().toString().trim(), amount_to_send, null, null);
  }

  private void sendTheMoney(boolean isEth, String destinationAddress, double amount, String tokenSymbol, String tokenAddress) {

    FragmentManager fm = getActivity().getSupportFragmentManager();
    ConfirmPaymentDialog confirmPaymentDialog =
        ConfirmPaymentDialog.newInstance(isEth, destinationAddress, amount, tokenSymbol, tokenAddress);
    confirmPaymentDialog.show(fm, "confirm_dialog_fragment");
  }

  @OnClick(R.id.send_imageButton_scanqr) public void onClickScanQR() {
    Intent iScan = new Intent(getActivity(), QRScannerActivity.class);
    iScan.putExtra("type", "address");
    startActivity(iScan);
  }

  private void updateBalances(Long aLong) {

    // get the coinbase
    String encAddress = preferencesHelper.getAddress();

    String address = "";
    String decodedPassword = "";
    String decodedSeed = "";
    Cryptography cryptography = new Cryptography(getActivity());
    try {
      decodedPassword = cryptography.decryptData(preferencesHelper.getPassword());
      decodedSeed = cryptography.decryptData(preferencesHelper.getSeed());

      address = cryptography.decryptData(encAddress);
    } catch (NoSuchPaddingException | NoSuchAlgorithmException | UnrecoverableEntryException | KeyStoreException | CertificateException | InvalidAlgorithmParameterException | IOException | InvalidKeyException | NoSuchProviderException | IllegalBlockSizeException | BadPaddingException e) {
      e.printStackTrace();
    }
  }
}