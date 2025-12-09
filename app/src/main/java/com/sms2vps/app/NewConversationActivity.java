package com.sms2vps.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;

import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import androidx.appcompat.widget.SwitchCompat;
import android.widget.Toast;
import java.util.ArrayList;
import android.view.View;
import android.widget.TextView;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import android.provider.ContactsContract;
import android.database.Cursor;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import java.util.Locale;
import androidx.recyclerview.widget.DiffUtil;
import android.view.inputmethod.InputMethodManager;

public class NewConversationActivity extends BaseActivity {
    private AutoCompleteTextView phoneNumberInput;
    private ArrayList<ContactItem> contacts;
    private ContactAdapter contactAdapter;
    private SwitchCompat readContactsSwitch;
    private static final int READ_CONTACTS_PERMISSION_REQUEST = 1;
 private RecyclerView contactsRecyclerView;
 private ContactsRecyclerAdapter contactsRecyclerAdapter;
 private SharedPreferences prefs;
 private static final String PREFS_NAME = "sms2vps_prefs";
 private static final String KEY_READ_CONTACTS_ENABLED = "read_contacts_enabled";
 private TextView fastScrollBubble;
 private Runnable bubbleHideRunnable;
 private boolean suppressSwitchCallback = false;

    private static class ContactAdapter extends ArrayAdapter<ContactItem> implements Filterable {
        private final ArrayList<ContactItem> filteredContacts;
        private final ArrayList<ContactItem> originalContacts;

        public ContactAdapter(@NonNull Context context, @NonNull ArrayList<ContactItem> contacts) {
            super(context, android.R.layout.simple_dropdown_item_1line, contacts);
            this.originalContacts = new ArrayList<>(contacts);
            this.filteredContacts = new ArrayList<>(contacts);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            TextView textView = (TextView) view;
            if (position < filteredContacts.size()) {
                ContactItem contact = filteredContacts.get(position);
                textView.setText(contact.toString());
            }
            // Sovelletaan fonttikoko myös listan pääelementtiin
            FontSizeManager.getInstance(getContext()).applyFontSize(textView);
            return view;
        }

        @NonNull
        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View view = super.getDropDownView(position, convertView, parent);
            TextView textView = (TextView) view;
            if (position < filteredContacts.size()) {
                ContactItem contact = filteredContacts.get(position);
                textView.setText(contact.toString());
            }
            // Sovelletaan fonttikoko pudotusvalikon riveihin
            FontSizeManager.getInstance(getContext()).applyFontSize(textView);
            return view;
        }
        @NonNull
        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    ArrayList<ContactItem> filteredList = new ArrayList<>();

                    if (android.text.TextUtils.isEmpty(constraint)) {
                        filteredList.addAll(originalContacts);
                    } else {
                        String filterPattern = constraint.toString().toLowerCase().trim();
                        for (ContactItem item : originalContacts) {
                            if (item.name().toLowerCase().contains(filterPattern) || item.phoneNumber().contains(filterPattern)) {
                                filteredList.add(item);
                            }
                        }
                    }

                    results.values = filteredList;
                    results.count = filteredList.size();
                    return results;
                }

                @Override
                @SuppressWarnings("unchecked")
                protected void publishResults(CharSequence constraint, @NonNull FilterResults results) {
                    filteredContacts.clear();
                    if (results.values != null) {
                        filteredContacts.addAll((ArrayList<ContactItem>) results.values);
                    }
                    notifyDataSetChanged();
                }
            };
        }

        @Nullable
        public ContactItem getContact(int position) {
            if (position >= 0 && position < filteredContacts.size()) {
                return filteredContacts.get(position);
            }
            return null;
        }

        @Override
        public int getCount() {
            return filteredContacts.size();
        }

        @Nullable
        @Override
        public ContactItem getItem(int position) {
            return getContact(position);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_conversation);

        phoneNumberInput = findViewById(R.id.phoneNumberInput);
        readContactsSwitch = findViewById(R.id.read_contacts_switch);
        contactsRecyclerView = findViewById(R.id.contactsRecyclerView);
        contactsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        contactsRecyclerView.setClipToPadding(false);
        ViewCompat.setOnApplyWindowInsetsListener(contactsRecyclerView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });
        fastScrollBubble = findViewById(R.id.fastScrollBubble);
        if (fastScrollBubble != null) {
            fastScrollBubble.setVisibility(View.GONE);
        }
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Palauta kytkimen tila muistiin tallennettuna, oletus OFF ensimmäisellä kerralla
        boolean savedState = prefs.getBoolean(KEY_READ_CONTACTS_ENABLED, false);
        readContactsSwitch.setChecked(savedState);

        readContactsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSwitchCallback) {
                return; // estä uudelleenkutsut kun päivitämme koodista kytkimen tilaa
            }
            // Tallenna tila pysyvästi
            prefs.edit().putBoolean(KEY_READ_CONTACTS_ENABLED, isChecked).apply();
            if (isChecked) {
                // Jos käyttöoikeus jo myönnetty, ladataan yhteystiedot suoraan
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                    loadContacts();
                    showContactsList(true);
                } else {
                    // Pyydetään käyttöoikeutta
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, READ_CONTACTS_PERMISSION_REQUEST);
                }
            } else {
                // Ilmoita, miten käyttöoikeuden voi perua manuaalisesti
                Toast.makeText(this, "To revoke permission, go to App Settings.", Toast.LENGTH_LONG).show();
                showContactsList(false);
            }
        });

        String mode = getIntent().getStringExtra("mode");

        if ("contact".equals(mode)) {
            readContactsSwitch.setVisibility(View.VISIBLE);
            phoneNumberInput.setHint("Enter a contact name");
            phoneNumberInput.setInputType(InputType.TYPE_CLASS_TEXT);
            phoneNumberInput.setThreshold(1);

            // Jos tila oli tallennettu ON:ksi, ja käyttöoikeus on jo myönnetty, näytä lista
            if (savedState && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                loadContacts();
                showContactsList(true);
            } else {
                showContactsList(false);
            }

            phoneNumberInput.setOnItemClickListener((parent, view, position, id) -> {
                ContactItem selectedContact = (contactAdapter != null) ? contactAdapter.getContact(position) : null;
                if (selectedContact != null) {
                    phoneNumberInput.setText(selectedContact.phoneNumber());
                    phoneNumberInput.dismissDropDown();
                }
            });

            // Päivitä bubble vierityksen mukaan
            contactsRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    if (contactsRecyclerView.getVisibility() != View.VISIBLE || contactsRecyclerAdapter == null) return;
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING || newState == RecyclerView.SCROLL_STATE_SETTLING) {
                        updateFastScrollBubble();
                        if (fastScrollBubble != null) {
                            fastScrollBubble.setVisibility(View.VISIBLE);
                            if (bubbleHideRunnable != null) fastScrollBubble.removeCallbacks(bubbleHideRunnable);
                        }
                    } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        if (fastScrollBubble != null) {
                            if (bubbleHideRunnable == null) {
                                bubbleHideRunnable = () -> fastScrollBubble.setVisibility(View.GONE);
                            }
                            fastScrollBubble.removeCallbacks(bubbleHideRunnable);
                            fastScrollBubble.postDelayed(bubbleHideRunnable, 600);
                        }
                    }
                }

                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    if (contactsRecyclerView.getVisibility() != View.VISIBLE || contactsRecyclerAdapter == null) return;
                    updateFastScrollBubble();
                }
            });

        } else {
            // Uusi numero -tilanne
            readContactsSwitch.setVisibility(View.GONE);
            phoneNumberInput.setInputType(InputType.TYPE_CLASS_PHONE);
            phoneNumberInput.setHint("Enter a phone number");
        }

        // Start Conversation -painikkeen käsittelijä
        Button startConversationButton = findViewById(R.id.start_conversation);
        startConversationButton.setOnClickListener(v -> {
            String phoneNumber = phoneNumberInput.getText().toString().trim();

            if (phoneNumber.isEmpty()) {
                Toast.makeText(this, "Please enter a phone number", Toast.LENGTH_SHORT).show();
                return;
            }

            // Käynnistä ConversationActivity puhelinnumerolla
            Intent intent = new Intent(this, ConversationActivity.class);
            intent.putExtra("phone_number", phoneNumber);
            if ("contact".equals(mode) && contacts != null) {
                // Etsi kontaktin nimi valitulle numerolle
                for (ContactItem contact : contacts) {
                    if (contact.phoneNumber().equals(phoneNumber)) {
                        intent.putExtra("contact_name", contact.name());
                        break;
                    }
                }
            }
            startActivity(intent);
            finish(); // Sulje this activity
        });
    }

    private void updateFastScrollBubble() {
        if (fastScrollBubble == null || contactsRecyclerAdapter == null) return;
        RecyclerView.LayoutManager lm = contactsRecyclerView.getLayoutManager();
        if (!(lm instanceof LinearLayoutManager)) return;
        int pos = ((LinearLayoutManager) lm).findFirstVisibleItemPosition();
        if (pos == RecyclerView.NO_POSITION) return;
        String section = contactsRecyclerAdapter.getSectionForPosition(pos);
        if (section != null) {
            fastScrollBubble.setText(section);
        }

        // Sijoita kupla peukalon tarkkaan Y-sijaintiin
        int extent = contactsRecyclerView.computeVerticalScrollExtent();
        int range = contactsRecyclerView.computeVerticalScrollRange();
        int offset = contactsRecyclerView.computeVerticalScrollOffset();
        if (range <= extent) {
            // Ei vieritystä, asemoidaan alkuun
            fastScrollBubble.setTranslationY(contactsRecyclerView.getPaddingTop());
            return;
        }
        float proportion = offset / (float) (range - extent);
        int thumbH = getResources().getDimensionPixelSize(R.dimen.fastscroll_thumb_height);
        int trackTop = contactsRecyclerView.getPaddingTop();
        int trackLen = contactsRecyclerView.getHeight() - contactsRecyclerView.getPaddingTop() - contactsRecyclerView.getPaddingBottom() - thumbH;
        float thumbTop = trackTop + proportion * trackLen;
        int bubbleH = fastScrollBubble.getHeight();
        if (bubbleH == 0) bubbleH = fastScrollBubble.getMeasuredHeight();
        float bubbleY = thumbTop + (thumbH / 2f) - (bubbleH / 2f);
        fastScrollBubble.setTranslationY(bubbleY);
    }

    // dpToPx poistettu; käytetään resurssidimensioita yhtenäiseen mitoitukseen

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == READ_CONTACTS_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Käyttöoikeus myönnetty
                Toast.makeText(this, "Read contacts permission granted.", Toast.LENGTH_SHORT).show();
                // Päivitä tallennettu tila ON:ksi
                prefs.edit().putBoolean(KEY_READ_CONTACTS_ENABLED, true).apply();
                // Päivitä kytkimen tila ilman kuuntelijan laukeamista
                suppressSwitchCallback = true;
                readContactsSwitch.setChecked(true);
                suppressSwitchCallback = false;
                // Järjestys: lataa kontaktit -> näytä lista
                loadContacts();
                showContactsList(true);
            } else {
                // Käyttöoikeus evätty
                Toast.makeText(this, "Read contacts permission denied.", Toast.LENGTH_SHORT).show();
                prefs.edit().putBoolean(KEY_READ_CONTACTS_ENABLED, false).apply();
                suppressSwitchCallback = true;
                readContactsSwitch.setChecked(false);
                suppressSwitchCallback = false;
                showContactsList(false);
            }
        }
    }

    @SuppressLint("Range")
    private void loadContacts() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            // Ei käyttöoikeutta, älä tee mitään
            return;
        }

        contacts = new ArrayList<>();
        Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                String number = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                // Varmista, ettei sijoiteta null-arvoja ContactItemiin (@NonNull)
                if (number == null || number.trim().isEmpty()) {
                    // Puuttuva numero: ohita rivi
                    continue;
                }
                String safeName = (name != null ? name.trim() : "");
                if (safeName.isEmpty()) {
                    // Jos nimi puuttuu, käytä numeroa nimenä
                    safeName = number;
                }
                contacts.add(new ContactItem(safeName, number));
            }
            cursor.close();
        }

        // Päivitä adapteri
        contactAdapter = new ContactAdapter(this, contacts);
        phoneNumberInput.setAdapter(contactAdapter);

        if (!contacts.isEmpty()) {
            Toast.makeText(this, "Contacts loaded.", Toast.LENGTH_SHORT).show();
        }

        // Bindaa myös RecyclerView-lista
        if (contactsRecyclerAdapter == null) {
            contactsRecyclerAdapter = new ContactsRecyclerAdapter(contacts, item -> {
                // Navigoi suoraan keskusteluun valitulla kontaktilla kuten Start-painike
                Intent intent = new Intent(this, ConversationActivity.class);
                intent.putExtra("phone_number", item.phoneNumber());
                intent.putExtra("contact_name", item.name());
                startActivity(intent);
                finish();
            }, this);
            contactsRecyclerView.setAdapter(contactsRecyclerAdapter);
            // Alustuksessa rows-lista on tyhjä; päivitä data, jotta rivit rakennetaan ja näkyvät
            contactsRecyclerAdapter.updateData(contacts);
        } else {
            contactsRecyclerAdapter.updateData(contacts);
        }

        // Älä muuta näkyvyyttä täällä; hoidetaan se luvan ja kutsujan mukaan
    }

    private void showContactsList(boolean show) {
        contactsRecyclerView.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            // Varmista, että lista tulee etualalle ja mitat päivittyvät
            contactsRecyclerView.bringToFront();
            contactsRecyclerView.requestLayout();
            contactsRecyclerView.invalidate();

            // Piilota näppäimistö ja poistetaan fokus syötekentästä, jotta lista ei peity
            if (phoneNumberInput != null) {
                phoneNumberInput.clearFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(phoneNumberInput.getWindowToken(), 0);
                }
                // Varmista, ettei AutoComplete-pudotusvalikko peitä listaa
                phoneNumberInput.dismissDropDown();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        String mode = getIntent().getStringExtra("mode");
        if (!"contact".equals(mode)) return;

        boolean enabled = prefs.getBoolean(KEY_READ_CONTACTS_ENABLED, false);
        if (!enabled) { showContactsList(false); return; }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            // Päivitä uusimpaan versioon palatessa
            loadContacts();
            showContactsList(true);
        } else {
            showContactsList(false);
        }
    }

    // RecyclerView adapter kontaktien näyttämiseen
    private static class ContactsRecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        interface OnContactClickListener { void onClick(ContactItem item); }

        private final ArrayList<ContactItem> data;
        private final ArrayList<Row> rows = new ArrayList<>();
        private final OnContactClickListener listener;
        private final Context context;

        ContactsRecyclerAdapter(ArrayList<ContactItem> data, OnContactClickListener listener, Context context) {
            this.data = new ArrayList<>(data);
            this.listener = listener;
            this.context = context;
        }

        void updateData(ArrayList<ContactItem> newData) {
            // Update data source
            this.data.clear();
            this.data.addAll(newData);

            // Build new rows for diffing
            final ArrayList<Row> oldRows = new ArrayList<>(rows);
            final ArrayList<Row> newRows = buildRowsFromData(this.data);

            DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() { return oldRows.size(); }

                @Override
                public int getNewListSize() { return newRows.size(); }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    Row old = oldRows.get(oldItemPosition);
                    Row nw = newRows.get(newItemPosition);
                    if (old.isHeader != nw.isHeader) return false;
                    if (old.isHeader) {
                        return old.header.equals(nw.header);
                    } else {
                        // Use phone number as stable id for a contact row
                        return old.item.phoneNumber().equals(nw.item.phoneNumber());
                    }
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    Row old = oldRows.get(oldItemPosition);
                    Row nw = newRows.get(newItemPosition);
                    if (old.isHeader) {
                        return old.header.equals(nw.header);
                    } else {
                        return old.item.name().equals(nw.item.name()) && old.item.phoneNumber().equals(nw.item.phoneNumber());
                    }
                }
            });

            rows.clear();
            rows.addAll(newRows);
            diff.dispatchUpdatesTo(this);
        }

        static class ContactVH extends RecyclerView.ViewHolder {
            TextView name;
            TextView number;
            ContactVH(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.contact_name);
                number = itemView.findViewById(R.id.contact_number);
            }
        }

        static class HeaderVH extends RecyclerView.ViewHolder {
            TextView letter;
            HeaderVH(@NonNull View itemView) {
                super(itemView);
                letter = itemView.findViewById(R.id.section_letter);
            }
        }

        private static final int VIEW_TYPE_HEADER = 0;
        private static final int VIEW_TYPE_ITEM = 1;

        private static class Row {
            final boolean isHeader;
            final String header;
            final ContactItem item;
            private Row(boolean isHeader, String header, ContactItem item) {
                this.isHeader = isHeader; this.header = header; this.item = item;
            }
            static Row header(String h) { return new Row(true, h, null); }
            static Row item(ContactItem i) { return new Row(false, null, i); }
        }

        // rebuildRows() poistettu; käytä buildRowsFromData(data) suoraan

        private ArrayList<Row> buildRowsFromData(ArrayList<ContactItem> src) {
            ArrayList<Row> built = new ArrayList<>();
            String currentSection = null;
            for (ContactItem ci : src) {
                String name = ci.name().trim();
                String letter;
                if (name.isEmpty()) {
                    letter = "#";
                } else {
                    char c = name.charAt(0);
                    if (Character.isLetter(c)) {
                        letter = String.valueOf(c).toUpperCase(Locale.getDefault());
                    } else {
                        letter = "#";
                    }
                }
                if (!letter.equals(currentSection)) {
                    currentSection = letter;
                    built.add(Row.header(letter));
                }
                built.add(Row.item(ci));
            }
            return built;
        }

        String getSectionForPosition(int position) {
            if (position < 0 || position >= rows.size()) return null;
            Row r = rows.get(position);
            if (r.isHeader) return r.header;
            String name = r.item.name().trim();
            if (name.isEmpty()) return "#";
            char c = name.charAt(0);
            return Character.isLetter(c)
                    ? String.valueOf(c).toUpperCase(Locale.getDefault())
                    : "#";
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_HEADER) {
                View v = android.view.LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.contact_section_header, parent, false);
                return new HeaderVH(v);
            } else {
                View v = android.view.LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.contact_list_item, parent, false);
                return new ContactVH(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            if (row.isHeader) {
                HeaderVH hvh = (HeaderVH) holder;
                hvh.letter.setText(row.header);
                FontSizeManager.getInstance(context).applyFontSize(hvh.letter);
            } else {
                ContactVH cvh = (ContactVH) holder;
                ContactItem item = row.item;
                cvh.name.setText(item.name());
                cvh.number.setText(item.phoneNumber());
                // Sovelletaan fonttikoko riville
                FontSizeManager.getInstance(context).applyFontSize(cvh.name);
                FontSizeManager.getInstance(context).applyFontSize(cvh.number);
                cvh.itemView.setOnClickListener(v -> listener.onClick(item));
            }
        }

        @Override
        public int getItemCount() { return rows.size(); }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).isHeader ? VIEW_TYPE_HEADER : VIEW_TYPE_ITEM;
        }
    }
}