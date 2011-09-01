package org.mconf.android;

import java.util.List;

import org.mconf.bbb.android.AboutDialog;
import org.mconf.bbb.android.BarcodeHandler;
import org.mconf.bbb.android.Client;
import org.mconf.bbb.android.R;
import org.mconf.web.Authentication;
import org.mconf.web.MconfWebImpl;
import org.mconf.web.MconfWebItf;
import org.mconf.web.Room;
import org.mconf.web.Space;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.View.OnTouchListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class LoginPage extends Activity {
	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(LoginPage.class);

	private static final int MENU_QR_CODE = Menu.FIRST;
	private static final int MENU_ABOUT = Menu.FIRST + 1;
	
	private final String DEFAULT_SERVER = "http://mconf.inf.ufrgs.br";
	private Authentication auth = null;
	private ArrayAdapter<String> spinnerAdapter;
	private Room selectedRoom = null;
	private MconfWebItf mconf = new MconfWebImpl();
	private BarcodeHandler barcodeHandler = new BarcodeHandler();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.login);
		
		loadCredentials();
		
		final EditText editTextUsername = (EditText) findViewById(R.id.editTextUsername);
		final EditText editTextPassword = (EditText) findViewById(R.id.editTextPassword);
		
		final Spinner spinnerRooms = (Spinner) findViewById(R.id.spinnerRooms);
		spinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
		spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerRooms.setAdapter(spinnerAdapter);
		
		spinnerRooms.setOnTouchListener(new OnTouchListener() {
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					if (auth == null
							|| !auth.isAuthenticated())
						auth = new Authentication(DEFAULT_SERVER, editTextUsername.getText().toString(), editTextPassword.getText().toString());
					if (auth.isAuthenticated()) {
						storeCredentials();
						List<Room> rooms = null;
						try {
							rooms = mconf.getRooms(auth);
						} catch (Exception e) {
							e.printStackTrace();
							Toast.makeText(LoginPage.this, R.string.login_cant_contact_server, Toast.LENGTH_SHORT).show();
							return true;
						}
						if (rooms.isEmpty()) {
							Toast.makeText(LoginPage.this, R.string.no_rooms, Toast.LENGTH_SHORT).show();
						} else {
							RoomsDialog dialog = new RoomsDialog(LoginPage.this, rooms);
							dialog.setOnSelectRoomListener(new RoomsDialog.OnSelectRoomListener() {
								
								@Override
								public void onSelectRoom(Room room) {
									selectedRoom = room;
									spinnerAdapter.clear();
									spinnerAdapter.add(room.getName());
									spinnerAdapter.notifyDataSetChanged();
								}
							});
							dialog.show();
						}
					} else {
						Toast.makeText(LoginPage.this, R.string.invalid_password, Toast.LENGTH_SHORT).show();
					}
					return true;
				} else
					return false;
			}
		});
		
		final Button buttonJoin = (Button) findViewById(R.id.buttonJoin);
		buttonJoin.setOnTouchListener(new OnTouchListener() {
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					if (selectedRoom == null) {
						Toast.makeText(LoginPage.this, R.string.select_room, Toast.LENGTH_SHORT).show();
						return true;
					}
					
					String path = null;
					try {
						path = mconf.getJoinUrl(auth, selectedRoom.getPath());
					} catch (Exception e) {
						if (selectedRoom.getOwner().getClass() == Space.class
								&& ((Space) selectedRoom.getOwner()).isPublic()
								&& !((Space) selectedRoom.getOwner()).isMember())
							Toast.makeText(LoginPage.this, R.string.no_running_meeting, Toast.LENGTH_SHORT).show();
						else
							Toast.makeText(LoginPage.this, R.string.login_cant_contact_server, Toast.LENGTH_SHORT).show();
						return true;
					}
					if (path == null
							|| path.length() == 0) {
						Toast.makeText(LoginPage.this, R.string.cant_join, Toast.LENGTH_SHORT).show();
						return true;
					}
	                Intent intent = new Intent(getApplicationContext(), Client.class);
	                intent.setAction(Intent.ACTION_VIEW);
	                intent.setData(new Uri.Builder()
	                		.scheme(getResources().getString(R.string.protocol))
	                		.appendEncodedPath(path.replace(getResources().getString(R.string.protocol) + ":/", ""))
	                		.build());
	                startActivity(intent);
	     
	                return true;
				}
				return false;
			}
		});
		
		final TextView account = (TextView) findViewById(R.id.textViewDontHaveAccount);
		account.setText(Html.fromHtml("<a href=\"" + DEFAULT_SERVER + "\">" + getResources().getString(R.string.dont_have_account) + "</a>"));
		account.setMovementMethod(LinkMovementMethod.getInstance());
		account.setLinkTextColor(Color.YELLOW);
	}
	
	private void loadCredentials() {
		final EditText editTextUsername = (EditText) findViewById(R.id.editTextUsername);
		final EditText editTextPassword = (EditText) findViewById(R.id.editTextPassword);
		final CheckBox checkBoxRememberMe = (CheckBox) findViewById(R.id.checkBoxRememberMe);
		
		SharedPreferences pref = getPreferences(MODE_PRIVATE);
		checkBoxRememberMe.setChecked(pref.getBoolean("rememberMe", false));
		editTextUsername.setText(pref.getString("username", ""));
		editTextPassword.setText(pref.getString("password", ""));
	}
	
	private void storeCredentials() {
		Editor pref = getPreferences(MODE_PRIVATE).edit();
		
		final EditText editTextUsername = (EditText) findViewById(R.id.editTextUsername);
		final EditText editTextPassword = (EditText) findViewById(R.id.editTextPassword);
		final CheckBox checkBoxRememberMe = (CheckBox) findViewById(R.id.checkBoxRememberMe);

		boolean rememberMe = checkBoxRememberMe.isChecked();
		String username = editTextUsername.getText().toString();
		String password = editTextPassword.getText().toString();
		pref.putBoolean("rememberMe", rememberMe);
		if (rememberMe) {
			pref.putString("username", username);
			pref.putString("password", password);
		} else {
			pref.putString("username", "");
			pref.putString("password", "");
		}
		pref.commit();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(Menu.NONE, MENU_QR_CODE, Menu.NONE, R.string.qrcode).setIcon(R.drawable.ic_menu_qrcode);
		menu.add(Menu.NONE, MENU_ABOUT, Menu.NONE, R.string.menu_about).setIcon(android.R.drawable.ic_menu_info_details);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) { 
		switch (item.getItemId()) {
		case MENU_ABOUT:
			new AboutDialog(this).show();
			return true; 
		case MENU_QR_CODE: 
			barcodeHandler.scan(this);
			return true;
		default:			
			return super.onOptionsItemSelected(item);
		}
	}    

	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (barcodeHandler.handle(requestCode, resultCode, intent)) {
			// handled
		}
	}
	
}