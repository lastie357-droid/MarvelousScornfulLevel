package com.a4455jkjh.apktool;

import android.Manifest;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A small SMS/MMS inbox owned by Master App.
 *
 * Reading and deleting messages is controlled by Android's SMS permissions and
 * default-SMS-role rules. The app never copies message bodies outside the
 * device's SMS provider.
 */
public class MessengerActivity extends ThemedActivity {
    private static final int SMS_PERMISSION_REQUEST = 1601;
    private static final int SMS_ROLE_REQUEST = 1602;
    private static final String PREFS = "master_messenger";
    private static final String BLOCKED_NUMBERS = "blocked_numbers";

    private LinearLayout content;
    private TextView status;
    private String activeAddress;

    private static class MessageRow {
        String id;
        String address;
        String body;
        long date;
        int type;
        String threadId;
    }

    private static class Conversation {
        String address;
        MessageRow latest;
        int count;
    }

    @Override
    protected void init(Bundle savedInstanceState) {
        setContentView(R.layout.messenger);
        content = findViewById(R.id.messenger_content);
        status = findViewById(R.id.messenger_status);
        findViewById(R.id.messenger_permissions).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestSmsPermissions();
            }
        });
        findViewById(R.id.messenger_default).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestDefaultSmsRole();
            }
        });
        findViewById(R.id.messenger_new).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showNewConversation();
            }
        });
        findViewById(R.id.messenger_refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showInbox();
            }
        });
        showInbox();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null && activeAddress == null) {
            showInbox();
        }
    }

    private boolean hasSmsPermission(String permission) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean canReadSms() {
        return hasSmsPermission(Manifest.permission.READ_SMS);
    }

    private void requestSmsPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            showInbox();
            return;
        }
        ArrayList<String> missing = new ArrayList<String>();
        String[] permissions = new String[] {
                Manifest.permission.READ_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS
        };
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        if (missing.isEmpty()) {
            showInbox();
        } else {
            requestPermissions(missing.toArray(new String[missing.size()]),
                    SMS_PERMISSION_REQUEST);
        }
    }

    private void requestDefaultSmsRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            showMessage(R.string.messenger_default_title,
                    R.string.messenger_default_older_android);
            return;
        }
        RoleManager roles = (RoleManager) getSystemService(ROLE_SERVICE);
        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_SMS)
                && !roles.isRoleHeld(RoleManager.ROLE_SMS)) {
            startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_SMS),
                    SMS_ROLE_REQUEST);
        } else {
            showMessage(R.string.messenger_default_title,
                    R.string.messenger_default_already);
        }
    }

    private void showInbox() {
        activeAddress = null;
        if (!canReadSms()) {
            status.setText(R.string.messenger_permission_needed);
            return;
        }
        status.setText(R.string.messenger_loading);
        content.removeAllViews();
        addHeaderActions();
        ArrayList<Conversation> conversations = readConversations();
        if (conversations.isEmpty()) {
            addEmptyMessage(R.string.messenger_no_messages);
            status.setText(R.string.messenger_ready);
            return;
        }
        status.setText(getString(R.string.messenger_conversation_count, conversations.size()));
        for (final Conversation conversation : conversations) {
            if (isBlocked(conversation.address)) {
                continue;
            }
            LinearLayout card = card();
            TextView title = label(conversation.address, 18, true);
            card.addView(title);
            TextView preview = label(
                    conversation.latest.body == null ? "" : conversation.latest.body,
                    14, false);
            preview.setMaxLines(2);
            card.addView(preview);
            TextView meta = label(DateFormat.getDateTimeInstance().format(
                    new Date(conversation.latest.date)), 11, false);
            card.addView(meta);
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showConversation(conversation.address);
                }
            });
            content.addView(card);
        }
    }

    private void addHeaderActions() {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button newButton = button(R.string.messenger_new_conversation);
        newButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showNewConversation();
            }
        });
        actions.addView(newButton, new LinearLayout.LayoutParams(0, 48, 1));
        Button refresh = button(R.string.messenger_refresh);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showInbox();
            }
        });
        actions.addView(refresh, new LinearLayout.LayoutParams(0, 48, 1));
        content.addView(actions);
    }

    private ArrayList<Conversation> readConversations() {
        LinkedHashMap<String, Conversation> grouped = new LinkedHashMap<String, Conversation>();
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    Uri.parse("content://sms"),
                    new String[] {"_id", "address", "body", "date", "type", "thread_id"},
                    null, null, "date DESC");
            if (cursor == null) {
                return new ArrayList<Conversation>();
            }
            int addressIndex = cursor.getColumnIndex("address");
            int bodyIndex = cursor.getColumnIndex("body");
            int dateIndex = cursor.getColumnIndex("date");
            int typeIndex = cursor.getColumnIndex("type");
            int idIndex = cursor.getColumnIndex("_id");
            int threadIndex = cursor.getColumnIndex("thread_id");
            while (cursor.moveToNext()) {
                String address = addressIndex >= 0 ? cursor.getString(addressIndex) : "";
                if (address == null || address.length() == 0) {
                    address = getString(R.string.messenger_unknown_number);
                }
                if (!grouped.containsKey(address)) {
                    MessageRow row = new MessageRow();
                    row.address = address;
                    row.body = bodyIndex >= 0 ? cursor.getString(bodyIndex) : "";
                    row.date = dateIndex >= 0 ? cursor.getLong(dateIndex) : 0;
                    row.type = typeIndex >= 0 ? cursor.getInt(typeIndex) : 1;
                    row.id = idIndex >= 0 ? cursor.getString(idIndex) : "";
                    row.threadId = threadIndex >= 0 ? cursor.getString(threadIndex) : "";
                    Conversation conversation = new Conversation();
                    conversation.address = address;
                    conversation.latest = row;
                    conversation.count = 1;
                    grouped.put(address, conversation);
                } else {
                    grouped.get(address).count++;
                }
            }
        } catch (SecurityException exception) {
            status.setText(R.string.messenger_permission_needed);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return new ArrayList<Conversation>(grouped.values());
    }

    private void showConversation(final String address) {
        activeAddress = address;
        content.removeAllViews();
        Button back = button(R.string.messenger_back);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showInbox();
            }
        });
        content.addView(back);
        TextView heading = label(address, 21, true);
        content.addView(heading);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button call = button(R.string.messenger_call);
        call.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openDialer(address);
            }
        });
        actions.addView(call, new LinearLayout.LayoutParams(0, 48, 1));
        Button block = button(isBlocked(address)
                ? R.string.messenger_unblock : R.string.messenger_block);
        block.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleBlocked(address);
                showConversation(address);
            }
        });
        actions.addView(block, new LinearLayout.LayoutParams(0, 48, 1));
        Button delete = button(R.string.messenger_delete);
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deleteConversation(address);
            }
        });
        actions.addView(delete, new LinearLayout.LayoutParams(0, 48, 1));
        content.addView(actions);

        for (MessageRow row : readMessages(address)) {
            TextView message = label((row.type == 2 ? "You: " : "")
                    + (row.body == null ? "" : row.body) + "\n"
                    + DateFormat.getDateTimeInstance().format(new Date(row.date)), 15, false);
            message.setPadding(14, 12, 14, 12);
            message.setBackgroundResource(R.drawable.master_button_secondary);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 8, 0, 0);
            content.addView(message, params);
        }

        Button reply = button(R.string.messenger_reply);
        reply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showCompose(address);
            }
        });
        content.addView(reply);
    }

    private ArrayList<MessageRow> readMessages(String address) {
        ArrayList<MessageRow> messages = new ArrayList<MessageRow>();
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    Uri.parse("content://sms"),
                    new String[] {"_id", "address", "body", "date", "type", "thread_id"},
                    "address = ?", new String[] {address}, "date ASC");
            if (cursor == null) {
                return messages;
            }
            while (cursor.moveToNext()) {
                MessageRow row = new MessageRow();
                row.id = cursor.getString(cursor.getColumnIndex("_id"));
                row.address = address;
                row.body = cursor.getString(cursor.getColumnIndex("body"));
                row.date = cursor.getLong(cursor.getColumnIndex("date"));
                row.type = cursor.getInt(cursor.getColumnIndex("type"));
                row.threadId = cursor.getString(cursor.getColumnIndex("thread_id"));
                messages.add(row);
            }
        } catch (SecurityException exception) {
            status.setText(R.string.messenger_permission_needed);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return messages;
    }

    private void showNewConversation() {
        showCompose("");
    }

    private void showCompose(String address) {
        final EditText number = new EditText(this);
        number.setHint(R.string.messenger_number_hint);
        number.setSingleLine(true);
        number.setText(address);
        final EditText body = new EditText(this);
        body.setHint(R.string.messenger_message_hint);
        body.setMinLines(3);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24, 0, 24, 0);
        box.addView(number);
        box.addView(body);
        new AlertDialog.Builder(this)
                .setTitle(R.string.messenger_new_conversation)
                .setView(box)
                .setPositiveButton(R.string.messenger_send,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                sendMessage(number.getText().toString(),
                                        body.getText().toString());
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void sendMessage(String address, String body) {
        if (address.trim().length() == 0 || body.trim().length() == 0) {
            Toast.makeText(this, R.string.messenger_missing_fields, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasSmsPermission(Manifest.permission.SEND_SMS)) {
            requestSmsPermissions();
            return;
        }
        try {
            SmsManager.getDefault().sendTextMessage(address.trim(), null, body, null, null);
            Toast.makeText(this, R.string.messenger_sent, Toast.LENGTH_SHORT).show();
            showConversation(address.trim());
        } catch (Exception exception) {
            Toast.makeText(this, R.string.messenger_send_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void deleteConversation(final String address) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.messenger_delete)
                .setMessage(R.string.messenger_delete_confirm)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            int deleted = getContentResolver().delete(
                                    Uri.parse("content://sms"), "address = ?",
                                    new String[] {address});
                            if (deleted == 0) {
                                Toast.makeText(MessengerActivity.this,
                                        R.string.messenger_delete_unavailable,
                                        Toast.LENGTH_LONG).show();
                            } else {
                                showInbox();
                            }
                        } catch (SecurityException exception) {
                            Toast.makeText(MessengerActivity.this,
                                    R.string.messenger_default_for_delete,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void openDialer(String address) {
        Intent dialer = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(address)));
        dialer.setPackage("com.google.android.dialer");
        try {
            startActivity(dialer);
        } catch (Exception exception) {
            Toast.makeText(this, R.string.phone_dialer_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private boolean isBlocked(String address) {
        return getSharedPreferences(PREFS, MODE_PRIVATE)
                .getStringSet(BLOCKED_NUMBERS, new HashSet<String>())
                .contains(address);
    }

    private void toggleBlocked(String address) {
        HashSet<String> blocked = new HashSet<String>(getSharedPreferences(PREFS, MODE_PRIVATE)
                .getStringSet(BLOCKED_NUMBERS, new HashSet<String>()));
        if (!blocked.add(address)) {
            blocked.remove(address);
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putStringSet(BLOCKED_NUMBERS, blocked).apply();
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(16, 12, 16, 12);
        card.setBackgroundResource(R.drawable.master_button_secondary);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 10, 0, 0);
        content.addView(card, params);
        return card;
    }

    private TextView label(String text, int size, boolean strong) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(getResources().getColor(R.color.master_text));
        label.setTextSize(size);
        label.setTypeface(null, strong ? android.graphics.Typeface.BOLD
                : android.graphics.Typeface.NORMAL);
        return label;
    }

    private Button button(int text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13);
        return button;
    }

    private void addEmptyMessage(int message) {
        TextView empty = label(getString(message), 16, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(12, 40, 12, 40);
        content.addView(empty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void showMessage(int title, int message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton(R.string.ok, null).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_REQUEST) {
            showInbox();
        }
    }
}