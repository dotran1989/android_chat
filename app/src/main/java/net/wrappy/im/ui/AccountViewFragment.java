/*
 * Copyright (C) 2008 Esmertec AG. Copyright (C) 2008 The Android Open Source
 * Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 * * Unless required by applicable law or agreed to in writing, software * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package net.wrappy.im.ui;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.provider.BaseColumns;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import net.wrappy.im.ImApp;
import net.wrappy.im.R;
import net.wrappy.im.crypto.IOtrChatSession;
import net.wrappy.im.crypto.otr.OtrAndroidKeyManagerImpl;
import net.wrappy.im.helper.AppFuncs;
import net.wrappy.im.model.ImConnection;
import net.wrappy.im.plugin.xmpp.XmppConnection;
import net.wrappy.im.provider.Imps;
import net.wrappy.im.service.IImConnection;
import net.wrappy.im.service.ImServiceConstants;
import net.wrappy.im.ui.legacy.ImPluginHelper;
import net.wrappy.im.ui.legacy.ProviderDef;
import net.wrappy.im.ui.legacy.SignInHelper;
import net.wrappy.im.ui.legacy.SimpleAlertHandler;
import net.wrappy.im.ui.onboarding.OnboardingManager;
import net.wrappy.im.util.BundleKeyConstant;
import net.wrappy.im.util.LogCleaner;
import net.wrappy.im.util.XmppUriHelper;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Locale;

public class AccountViewFragment extends Fragment {

    public static final String TAG = "AccountViewFragment";
    private long mProviderId = 0;
    private long mAccountId = 0;
    static final int REQUEST_SIGN_IN = 100001;
    private static final String[] ACCOUNT_PROJECTION = {Imps.Account._ID, Imps.Account.PROVIDER,
            Imps.Account.USERNAME,
            Imps.Account.PASSWORD,
            Imps.Account.KEEP_SIGNED_IN,
            Imps.Account.LAST_LOGIN_STATE};
    private static final int ACCOUNT_PROVIDER_COLUMN = 1;
    private static final int ACCOUNT_USERNAME_COLUMN = 2;
    private static final int ACCOUNT_PASSWORD_COLUMN = 3;

    private static final String USERNAME_VALIDATOR = "[^a-z0-9\\.\\-_\\+]";
    //    private static final int ACCOUNT_KEEP_SIGNED_IN_COLUMN = 4;
    //    private static final int ACCOUNT_LAST_LOGIN_STATE = 5;

    Uri mAccountUri;
    EditText mEditUserAccount;
    EditText mEditPass;
    EditText mEditPassConfirm;
    //  CheckBox mRememberPass;
    // CheckBox mUseTor;
    Button mBtnSignIn;
    Button mBtnQrDisplay;
    AutoCompleteTextView mSpinnerDomains;

    Button mBtnAdvanced;
    TextView mTxtFingerprint;

    //Imps.ProviderSettings.QueryMap settings;

    boolean isEdit = false;
    boolean isSignedIn = false;

    String mUserName = "";
    String mDomain = "";
    int mPort = 0;
    private String mOriginalUserAccount = "";

    private final static int DEFAULT_PORT = 5222;

    IOtrChatSession mOtrChatSession;
    private SignInHelper mSignInHelper;

    private boolean mIsNewAccount = false;

    private AsyncTask<Void, Void, String> mCreateAccountTask = null;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        if (mApp == null)
            initFragment();
    }

    private void initFragment() {

        Intent i = getIntent();

        mApp = (ImApp) getActivity().getApplication();

        String action = i.getAction();

        if (i.hasExtra(BundleKeyConstant.IS_SIGN_IN))
            isSignedIn = i.getBooleanExtra(BundleKeyConstant.IS_SIGN_IN, false);


        final ProviderDef provider;

        mSignInHelper = new SignInHelper(getActivity(), new SimpleAlertHandler(getActivity()));
        SignInHelper.SignInListener signInListener = new SignInHelper.SignInListener() {
            @Override
            public void connectedToService() {
            }

            @Override
            public void stateChanged(int state, long accountId) {
                if (state == ImConnection.LOGGED_IN) {
                    //  mSignInHelper.goToAccount(accountId);
                    // finish();
                    isSignedIn = true;
                } else {
                    isSignedIn = false;
                }

            }
        };

        mSignInHelper.setSignInListener(signInListener);


        ContentResolver cr = getActivity().getContentResolver();

        Uri uri = i.getData();
        // check if there is account information and direct accordingly
        if (Intent.ACTION_INSERT_OR_EDIT.equals(action)) {
            if ((uri == null) || !Imps.Account.CONTENT_ITEM_TYPE.equals(cr.getType(uri))) {
                action = Intent.ACTION_INSERT;
            } else {
                action = Intent.ACTION_EDIT;
            }
        }

        if (Intent.ACTION_INSERT.equals(action) && uri.getScheme().equals("ima")) {
            ImPluginHelper helper = ImPluginHelper.getInstance(getActivity());
            String authority = uri.getAuthority();
            String[] userpass_host = authority.split("@");
            String[] user_pass = userpass_host[0].split(":");
            mUserName = user_pass[0].toLowerCase(Locale.getDefault());
            String pass = user_pass[1];
            mDomain = userpass_host[1].toLowerCase(Locale.getDefault());
            mPort = 0;
            final boolean regWithTor = i.getBooleanExtra("useTor", false);

            Cursor cursor = openAccountByUsernameAndDomain(cr);
            boolean exists = cursor.moveToFirst();
            long accountId;
            if (exists) {
                accountId = cursor.getLong(0);
                mAccountUri = ContentUris.withAppendedId(Imps.Account.CONTENT_URI, accountId);
                pass = cursor.getString(ACCOUNT_PASSWORD_COLUMN);

                setAccountKeepSignedIn(true);
                mSignInHelper.activateAccount(mProviderId, accountId);
                mSignInHelper.signIn(pass, mProviderId, accountId, true);
                //  setResult(RESULT_OK);
                cursor.close();
                // finish();
                return;

            } else {
                mProviderId = helper.createAdditionalProvider(helper.getProviderNames().get(0)); //xmpp FIXME
                accountId = ImApp.insertOrUpdateAccount(cr, mProviderId, -1, mUserName, mUserName, pass);
                mAccountUri = ContentUris.withAppendedId(Imps.Account.CONTENT_URI, accountId);
                mSignInHelper.activateAccount(mProviderId, accountId);
                createNewAccount(mUserName, pass, accountId, regWithTor);
                cursor.close();
                return;
            }


        } else if (Intent.ACTION_INSERT.equals(action)) {


            mOriginalUserAccount = "";
            // TODO once we implement multiple IM protocols
            mProviderId = ContentUris.parseId(uri);
            provider = mApp.getProvider(mProviderId);


        } else if (Intent.ACTION_EDIT.equals(action)) {


            if ((uri == null) || !Imps.Account.CONTENT_ITEM_TYPE.equals(cr.getType(uri))) {
                LogCleaner.warn(ImApp.LOG_TAG, "<AccountActivity>Bad data");
                return;
            }

            isEdit = true;

            Cursor cursor = cr.query(uri, ACCOUNT_PROJECTION, null, null, null);

            if (cursor == null) {

                return;
            }

            if (!cursor.moveToFirst()) {
                cursor.close();

                return;
            }

            mAccountId = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID));

            mProviderId = cursor.getLong(ACCOUNT_PROVIDER_COLUMN);
            provider = mApp.getProvider(mProviderId);

            Cursor pCursor = cr.query(Imps.ProviderSettings.CONTENT_URI, new String[]{Imps.ProviderSettings.NAME, Imps.ProviderSettings.VALUE}, Imps.ProviderSettings.PROVIDER + "=?", new String[]{Long.toString(mProviderId)}, null);

            Imps.ProviderSettings.QueryMap settings = new Imps.ProviderSettings.QueryMap(
                    pCursor, cr, mProviderId, false /* don't keep updated */, null /* no handler */);

            try {
                mOriginalUserAccount = cursor.getString(ACCOUNT_USERNAME_COLUMN) + "@"
                        + settings.getDomain();
                mEditUserAccount.setText(mOriginalUserAccount);
                mEditPass.setText(cursor.getString(ACCOUNT_PASSWORD_COLUMN));
                //mRememberPass.setChecked(!cursor.isNull(ACCOUNT_PASSWORD_COLUMN));
                //mUseTor.setChecked(settings.getUseTor());
                mBtnQrDisplay.setVisibility(View.VISIBLE);
            } finally {
                settings.close();
                cursor.close();
            }


        } else {
            LogCleaner.warn(ImApp.LOG_TAG, "<AccountActivity> unknown intent action " + action);
            return;
        }

        setupUIPost();

    }

    private Intent getIntent() {
        return getActivity().getIntent();
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.account_activity, container, false);


        mIsNewAccount = getIntent().getBooleanExtra(BundleKeyConstant.REGISTER_KEY, false);


        mEditUserAccount = (EditText) view.findViewById(R.id.edtName);
        mEditUserAccount.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                checkUserChanged();
            }
        });

        mEditPass = (EditText) view.findViewById(R.id.edtPass);

        mEditPassConfirm = (EditText) view.findViewById(R.id.edtPassConfirm);
        mSpinnerDomains = (AutoCompleteTextView) view.findViewById(R.id.spinnerDomains);

        if (mIsNewAccount) {
            mEditPassConfirm.setVisibility(View.VISIBLE);
            mSpinnerDomains.setVisibility(View.VISIBLE);
            mEditUserAccount.setHint(R.string.account_setup_new_username);

            ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(),
                    android.R.layout.simple_dropdown_item_1line, OnboardingManager.getServers(getActivity()));
            mSpinnerDomains.setAdapter(adapter);

        }

//        mRememberPass = (CheckBox) findViewById(R.id.rememberPassword);
        //      mUseTor = (CheckBox) findViewById(R.id.useTor);


        mBtnSignIn = (Button) view.findViewById(R.id.btnSignIn);

        if (mIsNewAccount)
            mBtnSignIn.setText(R.string.btn_create_new_account);


        //mBtnAdvanced = (Button) findViewById(R.id.btnAdvanced);
        // mBtnQrDisplay = (Button) findViewById(R.id.btnQR);

        /*
        mRememberPass.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateWidgetState();
            }
        });*/

        return view;

    }

    private void setupUIPost() {
        Intent i = getIntent();

        if (isSignedIn) {
            mBtnSignIn.setText(getString(R.string.menu_sign_out));
            mBtnSignIn.setBackgroundResource(R.drawable.btn_red);
        }

        mEditUserAccount.addTextChangedListener(mTextWatcher);
        mEditPass.addTextChangedListener(mTextWatcher);

        mBtnAdvanced.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                showAdvanced();
            }
        });


        mBtnQrDisplay.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                showQR();

            }

        });


        mBtnSignIn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                checkUserChanged();

                /**
                 if (mUseTor.isChecked())
                 {
                 OrbotHelper oh = new OrbotHelper(AccountActivity.this);
                 if (!oh.isOrbotRunning())
                 {
                 oh.requestOrbotStart(AccountActivity.this);
                 return;
                 }
                 }*/


                final String pass = mEditPass.getText().toString();
                final String passConf = mEditPassConfirm.getText().toString();
                final boolean rememberPass = true;
                final boolean isActive = false; // TODO(miron) does this ever need to be true?
                ContentResolver cr = getActivity().getContentResolver();

                if (mIsNewAccount) {
                    mDomain = mSpinnerDomains.getText().toString();
                    String fullUser = mEditUserAccount.getText().toString();

                    if (fullUser.indexOf("@") == -1)
                        fullUser += '@' + mDomain;

                    if (!parseAccount(fullUser)) {
                        mEditUserAccount.selectAll();
                        mEditUserAccount.requestFocus();
                        return;
                    }

                    ImPluginHelper helper = ImPluginHelper.getInstance(getActivity());
                    mProviderId = helper.createAdditionalProvider(helper.getProviderNames().get(0)); //xmpp FIXME

                } else {
                    if (!parseAccount(mEditUserAccount.getText().toString())) {
                        mEditUserAccount.selectAll();
                        mEditUserAccount.requestFocus();
                        return;
                    } else {
                        settingsForDomain(mDomain, mPort);//apply final settings
                    }
                }


                mAccountId = ImApp.insertOrUpdateAccount(cr, mProviderId, -1, mUserName, mUserName,
                        rememberPass ? pass : null);

                mAccountUri = ContentUris.withAppendedId(Imps.Account.CONTENT_URI, mAccountId);

                //if remember pass is true, set the "keep signed in" property to true
                if (mIsNewAccount) {
                    if (pass.equals(passConf)) {
                        setAccountKeepSignedIn(rememberPass);

                        createNewAccount(mUserName, pass, mAccountId, false);

                    } else {
                        AppFuncs.alert(getActivity(), getString(R.string.error_account_password_mismatch), false);
                    }
                } else {
                    if (isSignedIn) {
                        signOut();
                        isSignedIn = false;
                    } else {
                        setAccountKeepSignedIn(rememberPass);
                        mSignInHelper.signIn(pass, mProviderId, mAccountId, isActive);
                        isSignedIn = true;
                    }
                    updateWidgetState();
                }

            }
        });

        /**
         mUseTor.setOnCheckedChangeListener(new OnCheckedChangeListener() {
        @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        updateUseTor(isChecked);
        }
        });*/

        updateWidgetState();

        if (i.hasExtra(BundleKeyConstant.TITLE_KEY)) {
            String title = i.getExtras().getString(BundleKeyConstant.TITLE_KEY);
        }

        if (i.hasExtra(BundleKeyConstant.NEW_USER_KEY)) {
            String newuser = i.getExtras().getString(BundleKeyConstant.NEW_USER_KEY);
            mEditUserAccount.setText(newuser);

            parseAccount(newuser);
            settingsForDomain(mDomain, mPort);

        }

        if (i.hasExtra(BundleKeyConstant.NEW_PASS_KEY)) {
            mEditPass.setText(i.getExtras().getString(BundleKeyConstant.NEW_PASS_KEY));
            mEditPass.setVisibility(View.GONE);
            //mRememberPass.setChecked(true);
            //mRememberPass.setVisibility(View.GONE);
        }
    }

    private boolean checkForKey(String userid) {

        try {
            OtrAndroidKeyManagerImpl otrKeyMan = OtrAndroidKeyManagerImpl.getInstance(getActivity());
            String fp = otrKeyMan.getLocalFingerprint(userid);

            if (fp == null) {
                otrKeyMan.generateLocalKeyPair(userid);
                fp = otrKeyMan.getLocalFingerprint(userid);
                return true;
            } else
                return true;
        } catch (Exception e) {
            Log.e(ImApp.LOG_TAG, "error checking for key", e);
            return false;
        }

    }

    private Cursor openAccountByUsernameAndDomain(ContentResolver cr) {
        String clauses = Imps.Account.USERNAME + " = ? AND " + Imps.ProviderSettings.VALUE + " = ?";
        String args[] = new String[2];
        args[0] = mUserName;
        args[1] = mDomain;

        String[] projection = {Imps.Account._ID};
        Cursor cursor = cr.query(Imps.Account.BY_DOMAIN_URI, projection, clauses, args, null);
        return cursor;
    }

    /*
    @Override
    protected void onDestroy() {

        if (mCreateAccountTask != null && (!mCreateAccountTask.isCancelled()))
        {
            mCreateAccountTask.cancel(true);
        }

        if (mSignInHelper != null)
            mSignInHelper.stop();

        super.onDestroy();
    }*/

/*
    private void getOTRKeyInfo() {

        if (mApp != null && FFF != null) {
            try {
                otrKeyManager = mApp.getRemoteImService().getOtrKeyManager(mOriginalUserAccount);

                if (otrKeyManager == null) {
                    mTxtFingerprint = ((TextView) findViewById(R.id.txtFingerprint));

                    String localFingerprint = otrKeyManager.getLocalFingerprint();
                    if (localFingerprint != null) {
                        ((TextView) findViewById(R.id.lblFingerprint)).setVisibility(View.VISIBLE);
                        mTxtFingerprint.setText(processFingerprint(localFingerprint));
                    } else {
                        ((TextView) findViewById(R.id.lblFingerprint)).setVisibility(View.GONE);
                        mTxtFingerprint.setText("");
                    }
                } else {
                    //don't need to notify people if there is nothing to show here
//                    AppFuncs.alert(this, "OTR is not initialized yet", false);
                }

            } catch (Exception e) {
                Log.e(ImApp.LOG_TAG, "error on create", e);

            }
        }

    }*/

    private void checkUserChanged() {
        if (mEditUserAccount != null) {
            String username = mEditUserAccount.getText().toString().trim().toLowerCase();

            if ((!username.equals(mOriginalUserAccount)) && parseAccount(username)) {
                //Log.i(TAG, "Username changed: " + mOriginalUserAccount + " != " + username);
                settingsForDomain(mDomain, mPort);
                mOriginalUserAccount = username;

            }
        }


    }

    boolean parseAccount(String userField) {
        boolean isGood = true;
        String[] splitAt = userField.trim().split("@");
        mUserName = splitAt[0].toLowerCase(Locale.ENGLISH).replaceAll(USERNAME_VALIDATOR, "");
        mDomain = "";
        mPort = 0;

        if (splitAt.length > 1) {
            mDomain = splitAt[1].toLowerCase(Locale.ENGLISH);
            String[] splitColon = mDomain.split(":");
            mDomain = splitColon[0].toLowerCase(Locale.ENGLISH);
            if (splitColon.length > 1) {
                try {
                    mPort = Integer.parseInt(splitColon[1]);
                } catch (NumberFormatException e) {
                    // TODO move these strings to strings.xml
                    isGood = false;
                    AppFuncs.alert(
                            getActivity(),
                            "The port value '" + splitColon[1]
                                    + "' after the : could not be parsed as a number!",
                            true);
                }
            }
        }

        //its okay if domain is null;

//        if (mDomain == null) {
        //          isGood = false;
        //AppFuncs.alert(AccountActivity.this,
        //	R.string.account_wizard_no_domain_warning,
        //	true);
        //    }
        /*//removing requirement of a . in the domain
        else if (mDomain.indexOf(".") == -1) {
            isGood = false;
            //	AppFuncs.alert(AccountActivity.this,
            //		R.string.account_wizard_no_root_domain_warning,
            //	true);
        }*/

        return isGood;
    }

    /*
     * If we know the direct XMPP server for a domain, we should turn off DNS lookup
     * because it is slow, error prone, and a way to leak information from third parties
     */
    void settingsForDomain(String domain, int port) {

        ContentResolver cr = getActivity().getContentResolver();
        Cursor pCursor = cr.query(Imps.ProviderSettings.CONTENT_URI, new String[]{Imps.ProviderSettings.NAME, Imps.ProviderSettings.VALUE}, Imps.ProviderSettings.PROVIDER + "=?", new String[]{Long.toString(mProviderId)}, null);

        Imps.ProviderSettings.QueryMap settings = new Imps.ProviderSettings.QueryMap(
                pCursor, cr, mProviderId, false /* don't keep updated */, null /* no handler */);

        settingsForDomain(domain, port, settings);

        settings.close();

    }

    private void settingsForDomain(String domain, int port, Imps.ProviderSettings.QueryMap settings) {


        settings.setRequireTls(true);
        settings.setTlsCertVerify(true);
        settings.setAllowPlainAuth(false);
        settings.setPort(DEFAULT_PORT);

        settings.setDomain(domain);
        settings.setPort(port);

        settings.requery();
    }

    private Handler mHandler = new Handler();
    private ImApp mApp = null;

    void signOut() {
        //if you are signing out, then we will deactive "auto" sign in
        ContentValues values = new ContentValues();
        values.put(Imps.AccountColumns.KEEP_SIGNED_IN, 0);
        getActivity().getContentResolver().update(mAccountUri, values, null, null);

        mApp = (ImApp) getActivity().getApplication();

        mApp.callWhenServiceConnected(mHandler, new Runnable() {
            @Override
            public void run() {

                signOut(mProviderId, mAccountId);
            }
        });

    }

    void signOut(long providerId, long accountId) {

        try {

            IImConnection conn = mApp.getConnection(providerId, accountId);
            if (conn != null) {
                conn.logout();
            } else {
                // Normally, we can always get the connection when user chose to
                // sign out. However, if the application crash unexpectedly, the
                // status will never be updated. Clear the status in this case
                // to make it recoverable from the crash.
                ContentValues values = new ContentValues(2);
                values.put(Imps.AccountStatusColumns.PRESENCE_STATUS, Imps.CommonPresenceColumns.OFFLINE);
                values.put(Imps.AccountStatusColumns.CONNECTION_STATUS, Imps.ConnectionStatus.OFFLINE);
                String where = Imps.AccountStatusColumns.ACCOUNT + "=?";
                getActivity().getContentResolver().update(Imps.AccountStatus.CONTENT_URI, values, where,
                        new String[]{Long.toString(accountId)});
            }
        } catch (RemoteException ex) {
            Log.e(ImApp.LOG_TAG, "signout: caught ", ex);
        } finally {

            //    AppFuncs.alert(this,
            //            getString(R.string.signed_out_prompt, this.mEditUserAccount.getText()),
            //            false);
            isSignedIn = false;

            mBtnSignIn.setText(getString(R.string.sign_in));
            mBtnSignIn.setBackgroundResource(R.drawable.btn_green);
        }
    }

    void createNewaccount(long accountId) {

        ContentValues values = new ContentValues(2);

        values.put(Imps.AccountStatusColumns.PRESENCE_STATUS, Imps.CommonPresenceColumns.NEW_ACCOUNT);
        values.put(Imps.AccountStatusColumns.CONNECTION_STATUS, Imps.ConnectionStatus.OFFLINE);
        String where = Imps.AccountStatusColumns.ACCOUNT + "=?";
        getActivity().getContentResolver().update(Imps.AccountStatus.CONTENT_URI, values, where,
                new String[]{Long.toString(accountId)});


    }

    void updateWidgetState() {
        boolean goodUsername = mEditUserAccount.getText().length() > 0;
        boolean goodPassword = mEditPass.getText().length() > 0;
        boolean hasNameAndPassword = goodUsername && goodPassword;

        mEditPass.setEnabled(goodUsername);
        mEditPass.setFocusable(goodUsername);
        mEditPass.setFocusableInTouchMode(goodUsername);

        //mRememberPass.setEnabled(hasNameAndPassword);
        //mRememberPass.setFocusable(hasNameAndPassword);

        mEditUserAccount.setEnabled(!isSignedIn);
        mEditPass.setEnabled(!isSignedIn);

        if (!isSignedIn) {
            mBtnSignIn.setEnabled(hasNameAndPassword);
            mBtnSignIn.setFocusable(hasNameAndPassword);
        } else {

        }
    }

    private final TextWatcher mTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int before, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int after) {
            updateWidgetState();

        }

        @Override
        public void afterTextChanged(Editable s) {

        }
    };

    private void deleteAccount() {

        //need to delete
        ((ImApp) getActivity().getApplication()).deleteAccount(getActivity().getContentResolver(), mAccountId, mProviderId);

    }

    private void showAdvanced() {

        checkUserChanged();

        Intent intent = new Intent(getActivity(), AccountSettingsActivity.class);
        intent.putExtra(ImServiceConstants.EXTRA_INTENT_PROVIDER_ID, mProviderId);
        startActivity(intent);
    }

    public void createNewAccount(final String usernameNew, final String passwordNew, final long newAccountId, final boolean useTor) {
        if (mCreateAccountTask != null && (!mCreateAccountTask.isCancelled())) {
            mCreateAccountTask.cancel(true);
        }
        mCreateAccountTask = new CreateAccountTask(getActivity(), newAccountId, usernameNew, passwordNew);
        mCreateAccountTask.execute();
    }

    public void showQR() {
        String localFingerprint = OtrAndroidKeyManagerImpl.getInstance(getActivity()).getLocalFingerprint(mOriginalUserAccount);
        String uri = XmppUriHelper.getUri(mOriginalUserAccount, localFingerprint);
        //  new IntentIntegrator(this).shareText(uri);
    }

    private void setAccountKeepSignedIn(final boolean rememberPass) {
        ContentValues values = new ContentValues();
        values.put(Imps.AccountColumns.KEEP_SIGNED_IN, rememberPass ? 1 : 0);
        getActivity().getContentResolver().update(mAccountUri, values, null, null);
    }

    public class CreateAccountTask extends AsyncTask<Void, Void, String> {

        private ProgressDialog dialog;
        private long newAccountId;
        private String usernameNew;
        private String passwordNew;
        private Activity activity;
        private WeakReference<Activity> weakReference;

        public CreateAccountTask(Activity activity, long newAccountId, String usernameNew, String passwordNew) {
            this.newAccountId = newAccountId;
            this.usernameNew = usernameNew;
            this.passwordNew = passwordNew;
            this.activity = activity;
            weakReference = new WeakReference<>(activity);
        }

        @Override
        protected void onPreExecute() {
            dialog = new ProgressDialog(activity);
            dialog.setCancelable(true);
            dialog.setMessage(getString(R.string.registering_new_account_));
            dialog.show();
        }

        @Override
        protected String doInBackground(Void... params) {
            ContentResolver cr = getActivity().getContentResolver();
            Cursor pCursor = cr.query(Imps.ProviderSettings.CONTENT_URI, new String[]{Imps.ProviderSettings.NAME, Imps.ProviderSettings.VALUE}, Imps.ProviderSettings.PROVIDER + "=?", new String[]{Long.toString(mProviderId)}, null);

            Imps.ProviderSettings.QueryMap settings = new Imps.ProviderSettings.QueryMap(
                    pCursor, cr, mProviderId, false /* don't keep updated */, null /* no handler */);

            try {
                settingsForDomain(mDomain, mPort, settings);

                HashMap<String, String> aParams = new HashMap<String, String>();

                XmppConnection xmppConn = new XmppConnection(getActivity());

                xmppConn.initUser(mProviderId, newAccountId);
                xmppConn.registerAccount(settings, usernameNew, passwordNew, aParams);
                // settings closed in registerAccount
            } catch (Exception e) {
                LogCleaner.error(ImApp.LOG_TAG, "error registering new account", e);

                return e.getLocalizedMessage();
            } finally {

                settings.close();
            }

            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            if (weakReference == null || weakReference.get() == null)
                return;

            try {
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                    dialog = null;
                }
            } catch (java.lang.IllegalArgumentException iae) {
                //dialog may not be attached to window if Activity was closed
            }

            if (result != null) {
                AppFuncs.alert(getActivity(), "error creating account: " + result, true);
                //AccountActivity.this.setResult(Activity.RESULT_CANCELED);
                //AccountActivity.this.finish();
            } else {
                mSignInHelper.activateAccount(mProviderId, newAccountId);
                mSignInHelper.signIn(passwordNew, mProviderId, newAccountId, true);

//                    AccountViewFragment.this.setResult(Activity.RESULT_OK);
                //                  AccountViewFragment.this.finish();
            }
        }
    }
}
