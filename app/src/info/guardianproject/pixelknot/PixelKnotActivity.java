package info.guardianproject.pixelknot;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import info.guardianproject.f5android.plugins.f5.Embed;
import info.guardianproject.f5android.plugins.f5.Embed.EmbedListener;
import info.guardianproject.f5android.plugins.f5.Extract;
import info.guardianproject.f5android.plugins.f5.Extract.ExtractionListener;
import info.guardianproject.f5android.plugins.PluginNotificationListener;
import info.guardianproject.pixelknot.Constants.PixelKnot.Keys;
import info.guardianproject.pixelknot.Constants.Screens.Loader;
import info.guardianproject.pixelknot.crypto.Aes;
import info.guardianproject.pixelknot.screens.CoverImageFragment;
import info.guardianproject.pixelknot.screens.DecryptImageFragment;
import info.guardianproject.pixelknot.screens.OnFailureDialog;
import info.guardianproject.pixelknot.screens.OnLoaderCanceledDialog;
import info.guardianproject.pixelknot.screens.PixelKnotLoader;
import info.guardianproject.pixelknot.screens.SelectModeDialog;
import info.guardianproject.pixelknot.screens.SetMessageFragment;
import info.guardianproject.pixelknot.screens.ShareFragment;
import info.guardianproject.pixelknot.screens.StegoImageFragment;
import info.guardianproject.pixelknot.screens.mods.PKDialogOnShowListener;
import info.guardianproject.pixelknot.utils.ActivityListener;
import info.guardianproject.pixelknot.utils.PixelKnotListener;
import info.guardianproject.pixelknot.utils.IO;
import info.guardianproject.pixelknot.utils.Image;
import info.guardianproject.pixelknot.utils.PixelKnotMediaScanner;
import info.guardianproject.pixelknot.utils.PixelKnotMediaScanner.MediaScannerListener;
import info.guardianproject.pixelknot.utils.PixelKnotNotification;
import info.guardianproject.f5android.stego.StegoProcessor;
import info.guardianproject.f5android.stego.StegoProcessThread;
import info.guardianproject.f5android.stego.StegoProcessorListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Vector;

public class PixelKnotActivity extends SherlockFragmentActivity implements Constants, PluginNotificationListener, PixelKnotListener, ViewPager.OnPageChangeListener, OnGlobalLayoutListener, MediaScannerListener, EmbedListener, ExtractionListener, StegoProcessorListener {
	private PKPager pk_pager;
	private ViewPager view_pager;

	File dump;
	int d, d_, last_diff, scale;
	private String last_locale;

	private View activity_root;
	private LinearLayout options_holder, action_display, action_display_;
	private LinearLayout progress_holder;
	private ActionBar action_bar;

	private static final String LOG = Logger.UI;

	private PixelKnot pixel_knot = new PixelKnot();

	PixelKnotLoader loader;
	PixelKnotNotification notification;
	ProgressDialog please_wait;

	Handler h = new Handler();
	InputMethodManager imm;
	boolean keyboardIsShowing = false;

	boolean hasSeenFirstPage = false;
	boolean hasSuccessfullyEmbed = false;
	boolean hasSuccessfullyExtracted = false;
	boolean hasSuccessfullyEncrypted = false;
	boolean hasSuccessfullyDecrypted = false;
	boolean hasSuccessfullyPasswordProtected = false;
	boolean hasSuccessfullyUnlocked = false;
	boolean isDecryptOnly = false;
	boolean canAutoAdvance = false;

	private List<TrustedShareActivity> trusted_share_activities;
	public ActivityManager am;
	
	private int steps_taken = 0;
	
	@SuppressLint("InflateParams")
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Log.d(LOG, "onCreate (main) called");
		
		dump = new File(DUMP);
		if(!dump.exists())
			dump.mkdir();

		d = R.drawable.progress_off_selected;
		d_ = R.drawable.progress_on;
		
		action_bar = getSupportActionBar();
		View action_bar_root = LayoutInflater.from(this).inflate(R.layout.action_bar, null);
		action_bar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
		action_bar.setCustomView(action_bar_root);
		
		setContentView(R.layout.pixel_knot_activity);
		
		am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

		options_holder = (LinearLayout) findViewById(R.id.options_holder);
		action_display = (LinearLayout) findViewById(R.id.action_display);
		action_display_ = (LinearLayout) action_bar_root.findViewById(R.id.action_display_);
		progress_holder = (LinearLayout) findViewById(R.id.progress_holder);
		
		if(Intent.ACTION_MAIN.equals(getIntent().getAction())) {
			initFragments(initForEncryption());
		} else {
			Log.d(LOG, "this type: " + getIntent().getType());
			
			/*
			 *  TODO: if this is a PGP-encrypted message (or just text tbd...), 
			 *  set to encryption mode and pre-input message
			 */
			
			DialogInterface.OnClickListener select_encrypt_listener = new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					initFragments(initForEncryption(resolveIntentData(getIntent())));
				}
			};
			
			DialogInterface.OnClickListener select_decrypt_listener = new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					initFragments(initForDecryption());
				}
			};
			
			AlertDialog ad = SelectModeDialog.getDialog(this, select_encrypt_listener, select_decrypt_listener);
			ad.setOnShowListener(new PKDialogOnShowListener(this));
			ad.show();
		}

		last_diff = 0;
		activity_root = findViewById(R.id.activity_root);
		activity_root.getViewTreeObserver().addOnGlobalLayoutListener(this);
		
		last_locale = PreferenceManager.getDefaultSharedPreferences(this).getString(Settings.LANGUAGE, "0");
	}
	
	private Bundle resolveIntentData(Intent intent) {
		Bundle args = new Bundle();
		Uri uri = intent.getData();
		
		try {
			args.putString(Keys.COVER_IMAGE_NAME, IO.pullPathFromUri(this, uri));
		} catch(NullPointerException e) {
			if(getIntent().hasExtra(Intent.EXTRA_STREAM)) {
				uri = (Uri) intent.getExtras().get(Intent.EXTRA_STREAM);
				args.putString(Keys.COVER_IMAGE_NAME, IO.pullPathFromUri(this, uri));
			} else if(getIntent().hasExtra(Intent.EXTRA_TEXT)) {
				uri = Uri.parse(intent.getStringExtra(Intent.EXTRA_TEXT));
				args.putString(Keys.COVER_IMAGE_NAME, IO.pullPathFromUri(this, uri));
			}
		}
		
		try {
			args.putString(Keys.COVER_IMAGE_URI, uri.toString());
		} catch(NullPointerException e) {
			// XXX: https://github.com/guardianproject/PixelKnot/issues/12
			// why no uri?
			failAndRestart(getString(R.string.could_not_load_file));
			return null;
		}
		
		Log.d(LOG, args.toString());
		return args;
	}
	
	private void initFragments(List<Fragment> fragments) {
		try {
			pixel_knot.clear();
		} catch(NullPointerException e) {}
		
		try {
			if(fragments == null) {
				return;
			}
		} catch(NullPointerException e) {
			return;
		}
		
		pk_pager = new PKPager(getSupportFragmentManager(), fragments);
		view_pager = (ViewPager) findViewById(R.id.fragment_holder);
		view_pager.setAdapter(pk_pager);
		view_pager.setOnPageChangeListener(this);

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		lp.setMargins(5, 0, 5, 0);

		for(int p=0; p<fragments.size(); p++) {
			ImageView progress_view = new ImageView(this);
			progress_view.setLayoutParams(lp);
			progress_view.setBackgroundResource(p == 0 ? d_ : d);
			progress_holder.addView(progress_view);
		}
	}
	
	private List<Fragment> initForEncryption() {
		return initForEncryption(null);
	}
	
	private List<Fragment> initForEncryption(Bundle image_args) {
		List<Fragment> fragments = new Vector<Fragment>();
		Fragment cover_image_fragment = Fragment.instantiate(this, CoverImageFragment.class.getName());
		
		if(image_args != null) {
			cover_image_fragment.setArguments(image_args);
		}
		
		Fragment set_message_fragment = Fragment.instantiate(this, SetMessageFragment.class.getName());
		Fragment share_fragment = Fragment.instantiate(this, ShareFragment.class.getName());

		fragments.add(0, cover_image_fragment);
		fragments.add(1, set_message_fragment);
		fragments.add(2, share_fragment);
		
		return fragments;
	}
	
	private List<Fragment> initForDecryption() {
		setIsDecryptOnly(true);
		
		List<Fragment> fragments = new Vector<Fragment>();
		
		Fragment stego_image_fragment = Fragment.instantiate(this, StegoImageFragment.class.getName());
		
		Bundle image_args = resolveIntentData(getIntent());
		if(image_args == null) {
			// TODO: fail nicely.
			return null;
		}
		
		stego_image_fragment.setArguments(image_args);

		Fragment decrypt_image_fragment = Fragment.instantiate(this, DecryptImageFragment.class.getName());
		Fragment share_fragment = Fragment.instantiate(this, ShareFragment.class.getName());

		fragments.add(0, stego_image_fragment);
		fragments.add(1, decrypt_image_fragment);
		fragments.add(2, share_fragment);
		
		return fragments;
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		Log.d(LOG, "onResume (main) called");
				
		String currentLocale = PreferenceManager.getDefaultSharedPreferences(this).getString(Settings.LANGUAGE, "0");
		if(!last_locale.equals(currentLocale))
			setNewLocale(currentLocale);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		doWait(false);
		Log.d(LOG, "onDestroy (main) called");
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		MenuInflater mi = getSupportMenuInflater();
		mi.inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case R.id.pk_about:
			showAbout();
			return true;
		case R.id.pk_faq:
			showFAQ();
			return true;
		case R.id.pk_preferences:
			last_locale = PreferenceManager.getDefaultSharedPreferences(this).getString(Settings.LANGUAGE, "0");
			startActivity(new Intent(this, Preferences.class));
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private void restart() {
		Log.d(LOG, "RESTARTING?");
		h.post(new Runnable() {
			@Override
			public void run() {
				getIntent().setData(null);

				Intent intent = getIntent();
				intent.setAction(Intent.ACTION_MAIN);
				
				if(intent.hasExtra(Intent.EXTRA_STREAM))
					intent.removeExtra(Intent.EXTRA_STREAM);
				else if(intent.hasExtra(Intent.EXTRA_TEXT))
					intent.removeExtra(Intent.EXTRA_TEXT);
			
				
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
				overridePendingTransition(0, 0);
				finish();

				overridePendingTransition(0, 0);
				startActivity(intent);
			}
		});
	}
	
	@SuppressLint("InflateParams")
	private void showAbout() {
		AlertDialog ad = AboutDialog.getDialog(this);
		ad.setOnShowListener(new PKDialogOnShowListener(this));
		ad.show();
	}
	
	private void showFAQ() {
		AlertDialog ad = FAQDialog.getDialog(this);
		ad.setOnShowListener(new PKDialogOnShowListener(this));
		ad.show();
	}

	@Override
	public void share() {
		Intent intent = new Intent();
		intent.setAction(Intent.ACTION_SEND);
		intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(pixel_knot.out_file));
		intent.setType("image/jpeg");
		startActivity(Intent.createChooser(intent, getString(R.string.embed_success)));
	}

	private void rearrangeForKeyboard(boolean state) {
		try {
			action_display_.removeView(options_holder);
			action_display.removeView(options_holder);
		} catch(NullPointerException e) {}

		if(state) {
			action_display.setVisibility(View.GONE);

			action_display_.setVisibility(View.VISIBLE);
			action_display_.addView(options_holder, action_display_.getChildCount());
			keyboardIsShowing = true;
		} else {
			action_display_.setVisibility(View.GONE);

			action_display.setVisibility(View.VISIBLE);
			action_display.addView(options_holder, action_display.getChildCount());
			keyboardIsShowing = false;
		}
	}
	
	@Override
	public void showKeyboard(View target) {
		if(!keyboardIsShowing) {
			imm.toggleSoftInput(0, InputMethodManager.SHOW_IMPLICIT);
			target.requestFocus();
		}
	}
	
	@Override
	public void hideKeyboard() {
		if(keyboardIsShowing)
			imm.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS);
	}
	
	public class TrustedShareActivity {
		public String app_name, package_name;
		public Drawable icon;
		public Intent intent;
		public View view;

		public TrustedShareActivity() {}

		public TrustedShareActivity(String app_name, String package_name) {
			this.app_name = app_name;
			this.package_name = package_name;			
		}

		public void setIcon(Drawable icon) {
			this.icon = icon;
		}

		private void setIntent() {
			intent = new Intent(Intent.ACTION_SEND)
				.setType("image/jpeg")
				.setPackage(package_name)
				.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(pixel_knot.out_file));

			view.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					PixelKnotActivity.this.startActivity(intent);
				}
			});
		}

		@SuppressLint("InflateParams")
		public void createView() {
			view = LayoutInflater.from(PixelKnotActivity.this).inflate(R.layout.trusted_share_activity_view, null);

			TextView app_name_holder = (TextView) view.findViewById(R.id.tsa_title);
			app_name_holder.setText(app_name);

			ImageView icon_holder = (ImageView) view.findViewById(R.id.tsa_icon);
			icon_holder.setImageDrawable(icon);
		}
	}
	
	public class PixelKnot extends JSONObject {
		String cover_image_name = null;
		String secret_message = null;
		
		// when password is not null, it will be atomized into 3: password, pw seed, and f5 seed
		String password = null;

		boolean can_save = false;
		boolean has_pgp_encryption = false;
		boolean password_override = false;

		File out_file = null;
		StegoProcessor stego_processor = null;
		
		DialogInterface.OnClickListener abort = new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {				
				if(stego_processor != null) {
					try {
						stego_processor.destroy();
					} catch(NullPointerException e) {
						Log.e(LOG, e.toString());
					}
										
				}
			}
		};

		public PixelKnot() {}

		private boolean checkIfReadyToSave() {
			try {
				if(getString(Keys.COVER_IMAGE_NAME) != null && getString(Keys.SECRET_MESSAGE) != null)
					return true;
			} catch(JSONException e) {}

			return false;
		}
		
		public void setCoverImageName(String cover_image_name) {
			this.cover_image_name = cover_image_name;
			try {
				put(Keys.COVER_IMAGE_NAME, cover_image_name);
			} catch (JSONException e) {}


			can_save = checkIfReadyToSave();
		}

		public void setSecretMessage(String secret_message) {
			this.secret_message = secret_message;
			
			try {
				if(!secret_message.equals(""))
					put(Keys.SECRET_MESSAGE, secret_message);
				else {
					this.secret_message = null;
					if(has(Keys.SECRET_MESSAGE))
						remove(Keys.SECRET_MESSAGE);
				}

			} catch(JSONException e) {}

			can_save = checkIfReadyToSave();
		}
		
		public boolean hasSecretMessage() {
			if(has(Keys.SECRET_MESSAGE)) {
				return true;
			}
			
			return false;
		}
		
		public void setPasswordOverride(boolean password_override) {
			this.password_override = password_override;
			if(password_override && hasPassword()) {
				remove(Keys.PASSWORD);
			}	
		}
		
		public boolean getPasswordOverride() {
			return this.password_override;
		}
		
		public void setPassphrase(String password) {
			this.password = password;

			try {
				put(Keys.PASSWORD, password);
			} catch(JSONException e) {}
		}

		public boolean hasPassword() {
			if(has(Keys.PASSWORD))
				return true;

			return false;
		}
		
		private String extractPasswordSalt(String from_password) {
			return from_password.substring((int) (from_password.length()/3), (int) ((from_password.length()/3)*2));
		}
		
		private String extractF5Seed(String from_password) {
			return from_password.substring((int) ((from_password.length()/3)*2));
		}
		
		private String extractPassword(String from_password) {
			return from_password.substring(0, (int) (from_password.length()/3));
		}
		
		public String getPassword() {
			if(!hasPassword()) {
				return null;
			}
			
			return extractPassword(password);
		}
		
		public byte[] getPasswordSalt() {
			if(!hasPassword()) {
				return Constants.DEFAULT_PASSWORD_SALT;
			}
			
			return extractPasswordSalt(password).getBytes();
		}
		
		public byte[] getF5Seed() {
			if(!hasPassword()) {
				return Constants.DEFAULT_F5_SEED;
			}
			
			return extractF5Seed(password).getBytes();
		}

		public void clear() {
			cover_image_name = null;
			secret_message = null;
			password = null;
			can_save = false;
			has_pgp_encryption = false;
			out_file = null;
			stego_processor = null;

			@SuppressWarnings("unchecked")
			Iterator<String> keys = keys();
			List<String> keys_ = new ArrayList<String>();
			while(keys.hasNext()) 
				keys_.add(keys.next());

			for(String key : keys_) {
				remove(key);		
			}
		}

		public void save() {
			if(hasSuccessfullyEmbed) {
				return;
			}

			final String oda_message = PixelKnotActivity.this.getString(R.string.abort_encryption);
			
			loader = new PixelKnotLoader(PixelKnotActivity.this, PixelKnotActivity.this.getString(R.string.encrypting)) {

				@Override
				public void onBackPressed() {
					AlertDialog ad = OnLoaderCanceledDialog.getDialog(PixelKnotActivity.this, oda_message, PixelKnot.this.abort);
					ad.setOnShowListener(new PKDialogOnShowListener(PixelKnotActivity.this));
					ad.show();
				}
			};
			loader.show();
			
			notification = new PixelKnotNotification(PixelKnotActivity.this, PixelKnotActivity.this.getString(R.string.encrypting));
			
			loader.init(4);
			notification.init(4);
			
			StegoProcessThread downsample = new StegoProcessThread(Logger.MODEL) {
				@Override
				public void run() {
					super.run();
					
					// TODO: actually, save to the same dir as original cover image, but with spoofed name
					setCoverImageName(Image.downsampleImage(pixel_knot.cover_image_name, dump));
					stego_processor.routeNext();
				}
			};
			pixel_knot.addProcess(downsample);
			
			if(pixel_knot.hasPassword()) {
				StegoProcessThread encrypt = new StegoProcessThread(Logger.AES) {
					
					@Override
					public void run() {
						super.run();
						
						onUpdate(PixelKnotActivity.this.getString(R.string.encrypting));
						Entry<String, String> pack = Aes.EncryptWithPassword(pixel_knot.getPassword(), secret_message, pixel_knot.getPasswordSalt()).entrySet().iterator().next();

						setSecretMessage(PASSWORD_SENTINEL.concat(new String(pack.getKey())).concat(pack.getValue()));
						
						hasSuccessfullyPasswordProtected = true;
						
						loader.update(Loader.Steps.ENCRYPT);
						notification.update(Loader.Steps.EMBED);
						
						stego_processor.routeNext();
					}
				};
				
				pixel_knot.addProcess(encrypt);
			} else {
				loader.update(Loader.Steps.EMBED);
				notification.update(Loader.Steps.EMBED);
			}
			
			Embed embed = new Embed(PixelKnotActivity.this, dump.getName(), cover_image_name, secret_message, pixel_knot.getF5Seed()) {
				@Override
				public void run() {
					this.secret_message = pixel_knot.secret_message;
					super.run();
				}
			};
			pixel_knot.addProcess((StegoProcessThread) embed);
		}

		public void extract() {
			Log.d(LOG, "now extracting " + cover_image_name);
			pixel_knot.out_file = new File(cover_image_name);
			
			final String oda_message = PixelKnotActivity.this.getString(R.string.abort_decryption);
			
			loader = new PixelKnotLoader(PixelKnotActivity.this, PixelKnotActivity.this.getString(R.string.decrypting)) {

				@Override
				public void onBackPressed() {
					AlertDialog ad = OnLoaderCanceledDialog.getDialog(PixelKnotActivity.this, oda_message, pixel_knot.abort);
					ad.setOnShowListener(new PKDialogOnShowListener(PixelKnotActivity.this));
					ad.show();
				}
			};
			loader.show();
			loader.init(Loader.Steps.EXTRACT);
			
			notification = new PixelKnotNotification(PixelKnotActivity.this, PixelKnotActivity.this.getString(R.string.decrypting));
			notification.init(Loader.Steps.EXTRACT);
			
			Extract extract = new Extract(PixelKnotActivity.this, cover_image_name, getF5Seed());
			pixel_knot.addProcess((StegoProcessThread) extract);
		}
		
		private void addProcess(StegoProcessThread thread) {
			if(thread != null) {
				if(stego_processor == null) {
					Log.d(LOG, "INITING STEGO PROCESS");
					stego_processor = new StegoProcessor(PixelKnotActivity.this, thread);
				} else {
					stego_processor.addThread(thread);
				}
				
				Log.d(LOG, "SETTING CURRENT PROCESS " + thread.getId());
			} else {
				Log.d(LOG, "JFYI Setting current process to null.");
				
			}
		}

		public void setOutFile(File out_file) {
			this.out_file = out_file;
			try {
				put(Keys.OUT_FILE_NAME, out_file.getAbsolutePath());
			} catch(JSONException e) {}

		}

		public boolean checkForPGPProtection() {
			if(hasSecretMessage()) {
				return (secret_message.contains(PGP_SENTINEL) && secret_message.indexOf(PGP_SENTINEL) == 0);
			}
			
			return false;
		}
		
		private boolean unlock() {
			if(!hasPassword() || !hasSecretMessage()) {
				return false;
			}
			
			if(secret_message.indexOf(PASSWORD_SENTINEL) != 0) {
				return false;
			}

			StegoProcessThread decrypt = new StegoProcessThread(Logger.AES) {
				
				@Override
				public void run() {
					super.run();
					
					secret_message = secret_message.substring(PASSWORD_SENTINEL.length());
					
					byte[] message = Base64.decode(secret_message.split("\n")[1], Base64.DEFAULT);
					byte[] iv =  Base64.decode(secret_message.split("\n")[0], Base64.DEFAULT);

					String sm = Aes.DecryptWithPassword(extractPassword(password), iv, message, extractPasswordSalt(password).getBytes());
					if(sm != null) {
						pixel_knot.setSecretMessage(sm);
						hasSuccessfullyUnlocked = true;
					} else {
						pixel_knot.setSecretMessage(new String(message));
					}
										
					h.post(new Runnable() {
						@Override
						public void run() {
							String result_text = PixelKnotActivity.this.getString(R.string.success);
							if(!hasSuccessfullyUnlocked) {
								result_text = PixelKnotActivity.this.getString(R.string.could_not_decrypt_message);
							}
							
							onProcessComplete(result_text);
							((ActivityListener) pk_pager.getItem(view_pager.getCurrentItem())).updateUi();
						}
					});
				}
			};
			
			addProcess(decrypt);
			return true;
		}
		
		private void PGPDecrypt() {
			// TODO: hook into PGP apps
		}
	}

	public class PKPager extends FragmentStatePagerAdapter {
		List<Fragment> fragments;
		FragmentManager fm;

		public PKPager(FragmentManager fm, List<Fragment> fragments) {
			super(fm);
			this.fm = fm;
			this.fragments = fragments;
		}

		@Override
		public Fragment getItem(int position) {
			return fragments.get(position);
		}

		@Override
		public int getCount() {
			return fragments.size();
		}
	}

	@Override
	public void setButtonOptions(ImageButton[] options) {
		try {
			options_holder.removeAllViews();
			
			for(ImageButton o : options) {
				try {
					((LinearLayout) o.getParent()).removeView(o);
				} catch(NullPointerException e) {}

				options_holder.addView(o);
			}

			rearrangeForKeyboard(false);
		} catch(NullPointerException e) {
			Log.d(LOG, "for some reason, options_holder is null?");	
		}
	}

	@Override
	public void updateButtonProminence(final int which, final int new_resource, final boolean enable) {
		h.postDelayed(new Runnable() {
			@Override
			public void run() {
				ImageButton ib = ((ImageButton) options_holder.getChildAt(which));
				ib.setImageResource(new_resource);
				ib.setEnabled(enable);
					
			}
		}, 1);

	}

	@Override
	public void onPageScrollStateChanged(int state) {
		if(state == 0) {
			h.post(new Runnable() {
				@Override
				public void run() {
					for(int v=0; v<progress_holder.getChildCount(); v++) {
						View view = progress_holder.getChildAt(v);
						if(view instanceof ImageView)
							((ImageView) view).setBackgroundResource(view_pager.getCurrentItem() == v ? d_ : d);
					}

					
					Fragment f = pk_pager.getItem(view_pager.getCurrentItem());
					
					if(view_pager.getCurrentItem() != 1)
						hideKeyboard();
					
					((ActivityListener) f).initButtons();
					((ActivityListener) f).updateUi();
					
				}
			});
		}
	}

	@Override
	public void onPageScrolled(int arg0, float arg1, int arg2) {}

	@Override
	public void onPageSelected(int page) {}

	@Override
	public PixelKnot getPixelKnot() {
		return pixel_knot;
	}

	@Override
	public void clearPixelKnot() {
		pixel_knot.clear();
		restart();
	}

	@Override
	public void onMediaScanned(String path, Uri uri) {
		Log.d(LOG, "media scan finished");
		if(!hasSuccessfullyEmbed) {
			pixel_knot.setCoverImageName(path);
			((CoverImageFragment) pk_pager.fragments.get(0)).setImageData(path, uri);
		}
	}

	@Override
	public void onExtractionResult(final ByteArrayOutputStream baos) {
		hasSuccessfullyExtracted = true;
		boolean is_complete_ = true;
		
		pixel_knot.setSecretMessage(new String(baos.toByteArray()));
		
		if(pixel_knot.hasPassword()) {
			is_complete_ = !pixel_knot.unlock();
		}
		
		if(pixel_knot.checkForPGPProtection()) {
			pixel_knot.PGPDecrypt();
		}
		
		final boolean is_complete = is_complete_;
		h.post(new Runnable() {
			@Override
			public void run() {
				String result_text = PixelKnotActivity.this.getString(R.string.message_extracted);
				
				if(!is_complete) {
					notification.post(result_text);
					pixel_knot.stego_processor.routeNext();
				} else {
					onProcessComplete(result_text);
					((ActivityListener) pk_pager.getItem(view_pager.getCurrentItem())).updateUi();
				}
			}
		});
	}

	@Override
	public void onEmbedded(final File out_file) {
		h.post(new Runnable() {
			@Override
			public void run() {
				hasSuccessfullyEmbed = true;
				
				if(Image.cleanUp(PixelKnotActivity.this, new String[] {pixel_knot.cover_image_name, new File(DUMP, "temp_img.jpg").getAbsolutePath()})) {
					out_file.renameTo(new File(pixel_knot.cover_image_name));
					pixel_knot.setOutFile(new File(pixel_knot.cover_image_name));
				} else
					pixel_knot.setOutFile(out_file);
				
				try {
					new PixelKnotMediaScanner(PixelKnotActivity.this, pixel_knot.getString(Keys.OUT_FILE_NAME));
				} catch (JSONException e) {
					Log.e(Logger.UI, e.toString());
					e.printStackTrace();
				}
				((ImageButton) options_holder.getChildAt(0)).setEnabled(true);
				
				String result_text = PixelKnotActivity.this.getString(R.string.message_embedded);
				onProcessComplete(result_text);

				((ActivityListener) pk_pager.getItem(view_pager.getCurrentItem())).updateUi();
			}
		});

	}

	@Override
	public void onGlobalLayout() {
		Rect r = new Rect();
		activity_root.getWindowVisibleDisplayFrame(r);

		int height_diff = activity_root.getRootView().getHeight() - (r.bottom - r.top);
		if(height_diff != last_diff) {
			if (height_diff > 100)
				rearrangeForKeyboard(true);
			else
				rearrangeForKeyboard(false);
		}

		last_diff = height_diff;
	}

	@Override
	public List<TrustedShareActivity> getTrustedShareActivities() {
		if(trusted_share_activities == null) {
			trusted_share_activities = new Vector<TrustedShareActivity>();

			Intent intent = new Intent(Intent.ACTION_SEND)
			.setType("image/*");

			PackageManager pm = getPackageManager();
			for(ResolveInfo ri : pm.queryIntentActivities(intent, 0)) {
				if(Arrays.asList(Image.TRUSTED_SHARE_ACTIVITIES).contains(Image.Activities.get(ri.activityInfo.packageName))) {
					try {
						ApplicationInfo ai = pm.getApplicationInfo(ri.activityInfo.packageName, 0);					
						TrustedShareActivity tsa = new TrustedShareActivity(Image.Activities.get(ri.activityInfo.packageName), ri.activityInfo.packageName);

						tsa.setIcon(pm.getApplicationIcon(ai));
						tsa.createView();
						tsa.setIntent();

						trusted_share_activities.add(tsa);

					} catch(PackageManager.NameNotFoundException e) {
						Log.e(Logger.UI, e.toString());
						e.printStackTrace();
						continue;
					}
				}
			}
		}

		return trusted_share_activities;
	}

	@Override
	public boolean getHasSeenFirstPage() {
		return hasSeenFirstPage;
	}

	@Override
	public void setHasSeenFirstPage(boolean hasSeenFirstPage) {
		this.hasSeenFirstPage = hasSeenFirstPage;
	}

	@Override
	public boolean getHasSuccessfullyEmbed() {
		return hasSuccessfullyEmbed;
	}

	@Override
	public void setHasSuccessfullyEmbed(boolean hasSuccessfullyEmbed) {
		this.hasSuccessfullyEmbed = hasSuccessfullyEmbed;

	}

	@Override
	public boolean getHasSuccessfullyExtracted() {
		return hasSuccessfullyExtracted;
	}

	@Override
	public void setHasSuccessfullyExtracted(boolean hasSuccessfullyExtracted) {
		this.hasSuccessfullyExtracted = hasSuccessfullyExtracted;
	}

	@Override
	public void setHasSuccessfullyEncrypted(boolean hasSuccessfullyEncrypted) {
		this.hasSuccessfullyEncrypted = hasSuccessfullyEncrypted; 

	}

	@Override
	public boolean getHasSuccessfullyEncrypted() {
		return hasSuccessfullyEncrypted;
	}

	@Override
	public boolean getHasSuccessfullyDecrypted() {
		return hasSuccessfullyDecrypted;
	}

	@Override
	public void setHasSuccessfullyDecrypted(boolean hasSuccessfullyDecrypted) {
		this.hasSuccessfullyDecrypted = hasSuccessfullyDecrypted;
	}

	@Override
	public boolean getHasSuccessfullyPasswordProtected() {
		return hasSuccessfullyPasswordProtected;
	}

	@Override
	public void setHasSuccessfullyPasswordProtected(boolean hasSuccessfullyPasswordProtected) {
		this.hasSuccessfullyPasswordProtected = hasSuccessfullyPasswordProtected;
	}

	@Override
	public boolean getHasSuccessfullyUnlocked() {
		return hasSuccessfullyUnlocked;
	}

	@Override
	public void setHasSuccessfullyUnlocked(boolean hasSuccessfullyUnlocked) {
		this.hasSuccessfullyUnlocked = hasSuccessfullyUnlocked;
	}
	
	@Override
	public void setIsDecryptOnly(boolean isDecryptOnly) {
		this.isDecryptOnly = isDecryptOnly;
	}

	@Override
	public boolean getIsDecryptOnly() {
		return isDecryptOnly;
	}

	@Override
	public void autoAdvance() {
		Log.d(LOG, "AUTO ADVANCING?");
		if(this.getCanAutoAdvance()) {
			autoAdvance(view_pager.getCurrentItem() == pk_pager.getCount() - 1 ? 0 : view_pager.getCurrentItem() + 1);
			setCanAutoAdvance(false);
		}
	}
	
	@Override
	public boolean getCanAutoAdvance() {
		return this.canAutoAdvance;
	}

	@Override
	public void setCanAutoAdvance(boolean canAutoAdvance) {
		this.canAutoAdvance = canAutoAdvance;
	}

	@Override
	public void autoAdvance(int position) {
		view_pager.setCurrentItem(position, true);		
	}
	
	private void setNewLocale(String locale_code) {
		Configuration configuration = new Configuration();
		switch(Integer.parseInt(locale_code)) {
		case Settings.Locales.DEFAULT:
			configuration.locale = new Locale(Locale.getDefault().getLanguage());
			break;
		case Settings.Locales.EN:
			configuration.locale = new Locale("en");
			break;
		case Settings.Locales.FA:
			configuration.locale = new Locale("fa");
			break;
		case Settings.Locales.DE:
			configuration.locale = new Locale("de");
			break;
		case Settings.Locales.EL:
			configuration.locale = new Locale("el");
			break;
		case Settings.Locales.ES:
			configuration.locale = new Locale("es");
			break;
		case Settings.Locales.VI:
			configuration.locale = new Locale("vi");
			break;
			
		}
		getResources().updateConfiguration(configuration, getResources().getDisplayMetrics());
		restart();
	}
	
	@Override
	public void onUpdate(String with_message) {
		steps_taken++;

		loader.post(with_message);
		notification.post();
		
		Log.d(Logger.UI, "steps taken: " + steps_taken);
	}

	@Override
	public void onFailure() {
		fail(getString(R.string.could_not_extract));
		Log.d(LOG, "ON PROCESS FAILED");
	}
	
	public void fail(String with_message) {
		fail(with_message, true);
	}
	
	public void fail(String with_message, boolean recoverable) {
		try {
			loader.fail(with_message);
		} catch(NullPointerException e) {
			Log.e(Logger.UI, e.toString());
			e.printStackTrace();
		}
		
		try {
			notification.fail(with_message, recoverable);
		} catch(NullPointerException e) {
			Log.e(Logger.UI, e.toString());
			e.printStackTrace();
		}
		
		// TODO: notify of failure instead of finish.  maybe retry.
	}
	
	private void failAndRestart(String with_message) {
		AlertDialog ad = OnFailureDialog.getDialog(PixelKnotActivity.this, with_message, new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				restart();
			}
			
		});
		
		ad.setOnShowListener(new PKDialogOnShowListener(PixelKnotActivity.this));
		ad.show();
	}
	
	@Override
	public void onProcessComplete(String result_text) {
		try {
			loader.finish(result_text);
		} catch(NullPointerException e) {
			Log.e(Logger.UI, e.toString());
			e.printStackTrace();
		}
		
		try{
			notification.finish(result_text);
		} catch(NullPointerException e) {
			Log.e(Logger.UI, e.toString());
			e.printStackTrace();
		}
		
		try {
			pixel_knot.stego_processor.cleanUp();
		} catch(NullPointerException e) {
			Log.e(Logger.UI, e.toString());
			e.printStackTrace();
		}
		
		Log.d(LOG, "ON PROCESS COMPLETE");
	}

	@Override
	public void doWait(boolean status) {
		if(status) {
			please_wait = new ProgressDialog(this);
			please_wait.setCancelable(false);
			please_wait.setMessage(getResources().getString(R.string.please_wait));
			
			please_wait.show();
		} else {
			try {
				please_wait.dismiss();
			} catch(NullPointerException e) {}
		}
		
	}

	@Override
	public void onProcessorQueueAborted() {
		fail(getString(R.string.process_aborted), false);
		Log.d(LOG, "ON PROCESS ABORTED");
		restart();
	}
}