package creativia.pager;

import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;

import java.net.MalformedURLException;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

public class SendPingActivity extends Activity implements OnClickListener {
	
	private String TAG = "Pager";
	private SocketIO socket;
	
	private EditText txtMessage;
	private EditText txtUsername;
	private EditText txtPassword;
	private ListView lstMessages;
	
	private List<String> listItems = new ArrayList<String>();
	private ArrayAdapter<String> adapter;
	
	static {
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
    }
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_ping);
        
        Button btnSend = (Button)findViewById(R.id.btnSend);
        btnSend.setOnClickListener(this);
        
        txtMessage = (EditText)findViewById(R.id.txtMessage);
        txtUsername = (EditText)findViewById(R.id.txtUsername);
        txtPassword = (EditText)findViewById(R.id.txtKey);
        lstMessages = (ListView)findViewById(R.id.lstMessages);
        
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, listItems);
        lstMessages.setAdapter(adapter);
        
        this.setupFieldSaving();
        this.connect();
    }
    
    private void setupFieldSaving() {
    	txtUsername.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				Editor e = getApplication().getSharedPreferences("user.settings", 0).edit();
				e.putString("username", s.toString());
				e.commit();
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			
			@Override
			public void afterTextChanged(Editable s) {}
		});
        
        txtUsername.setText(getApplication().getSharedPreferences("user.settings", 0).getString("username", ""));
    }
    
    private void connect() {
    	try {
        	socket = new SocketIO("http://46.9.215.37:8080/");
        }
        catch(MalformedURLException e) {
        	Log.d(TAG, "Unable to connect: " + e.getMessage());
        }
        
        socket.connect(this.ioCallback);
    }
    

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_send_ping, menu);
        return true;
    }

	@Override
	public void onClick(View v) {
		if(v.getId() == R.id.btnSend) {
			
		}
	}
	
	private void handleData(JSONArray data) throws JSONException {
		Log.d(TAG, "Incoming message: " + data.toString());
		
		final String to = data.getString(0);
		final String from = data.getString(1);
		JSONObject encryptedMessage = new JSONObject(data.getString(2));

		Log.d(TAG, "Message from " + from + " to " + to + " containing " + encryptedMessage);
		
		if(to.equals(txtUsername.getText().toString())) {
			final String msg = decryptMessage(encryptedMessage);
			
			Log.d(TAG, "This message is for you: " + msg);
			
			runOnUiThread(new Runnable() {
			     public void run() {
			    	listItems.add("<" + from +  "> " + msg);
					adapter.notifyDataSetChanged();
					
			     }
			});
		}
	}
	
	private String decryptMessage(JSONObject d) throws JSONException {
		try {
			String password = txtPassword.getText().toString();
			return DecoderRing.decodeMessage(password, d);
			
		} catch (Exception e) {
			Log.w(TAG, e);
			return "";
		} 
	}
 
	private IOCallback ioCallback = new IOCallback() {
		
		@Override
		public void onMessage(JSONObject payload, IOAcknowledge ack) {
			Log.d(TAG, "onMessage JSON: [" + payload.toString() + "]");
		}
		
		@Override
		public void onMessage(String payload, IOAcknowledge ack) {
			Log.d(TAG, "onMessage String: " + payload + "]");
		}
		
		@Override
		public void onError(SocketIOException error) {
			Log.w(TAG, error);
		}
		
		@Override
		public void onDisconnect() {
			Log.d(TAG, "Disconnected");
		}
		
		@Override
		public void onConnect() {
			Log.d(TAG, "Connected");
		}
		
		@Override
		public void on(String messageLabel, IOAcknowledge ack, Object... dataobjs) {
			//Log.d(TAG, "On: " + messageLabel);
			
			if(messageLabel.equalsIgnoreCase("serverData")) {
				//Log.d(TAG, "Server info with " + dataobjs.length + " objects");
				
				if(dataobjs.length == 1){
					Object data = dataobjs[0];
					Log.d(TAG, "Server data: " + data);
				}
			}
			
			if(messageLabel.equalsIgnoreCase("inMessage")) {
				//Log.d(TAG, "Incoming message with " + dataobjs.length + " objects");
				
				if(dataobjs.length == 1){
					Object data = dataobjs[0];

					if(data instanceof JSONArray) {
						try {
							handleData((JSONArray)data);
						}
						catch(JSONException e) {
							Log.d(TAG, "Message received was not valid pager JSON");
						}
					}
				}
					
			}
		}
	};
}
