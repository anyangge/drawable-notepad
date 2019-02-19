package com.example.tomek.notepad.activities;

import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import android.os.Build;
import android.speech.RecognizerIntent;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.text.Spannable;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.ActionMode;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.example.tomek.notepad.database.DatabaseHandler;
import com.example.tomek.notepad.views.DrawingView;
import com.example.tomek.notepad.model.Note;
import com.example.tomek.notepad.R;

import java.util.ArrayList;
import java.util.Date;

/**
 * Note Activity class that handles:
 * - New notes creation,
 * - Updating existing ones,
 * - Deleting existing ones if text is changed to blank
 * - Text formatting
 * - Drawing
 */
public class NoteActivity extends AppCompatActivity {

    //TODO fix bug that adds two empty lines into loaded note (fixed like a retard)
    //TODO Enable choice of voice matches?
    //TODO Voice input text from cursor position
    //TODO add Javadoc
    //TODO Delete Done button, add back button to left side of toolbar
    //TODO Add Background function to note edit, then display this background on ListView
    //TODO Clicking on "New note" on note edit activity changes note title
    //TODO Default note title system (handle blank text notes, with pictures on it)
    //TODO Toggle panels icons color
    //TODO Disable text/draw panel when back button is clicked. Prevents user from saving note when tries to close panel with back button

    // Draw mode booleans
    private boolean drawMode;

    // Drawing canvas
    private DrawingView drawingView;

    // Brush sizes
    private static final float
            SMALL_BRUSH = 5,
            MEDIUM_BRUSH = 10,
            LARGE_BRUSH = 20;

    // Request code for voice input
    private static final int REQUEST_CODE = 1234;

    // Database Handler
    private DatabaseHandler dbHandler;

    // Percent of total layout height that is prepared for format text panel
    // Default 0.3, Values  0 < x < 1
    private static final double MENU_MARGIN_RELATIVE_MODIFIER = 0.3;

    // format text and draw panel container
    private LinearLayout mSliderLayout;
    private LinearLayout mDrawLayout;

    // Actual Note ID
    // (is -1 when it's new note)
    private int noteID;

    // Title of note
    private EditText noteTitle;

    // EditText panel
    private EditText editText;

    // Spannable used to format text
    // Converted to HTML String in database
    private Spannable spannable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note);

        // Set Views fields values
        noteTitle = (EditText) findViewById(R.id.activity_note_title);
        editText = (EditText) findViewById(R.id.editText);
        mSliderLayout = (LinearLayout) findViewById(R.id.formatTextSlider);
        mDrawLayout = (LinearLayout) findViewById(R.id.drawPanelSlider);
        drawingView = (DrawingView) findViewById(R.id.drawing);

        // set boolean values
        drawMode = false;

        // Get params for format text panel and draw panel
        ViewGroup.LayoutParams paramsTextFormat = mSliderLayout.getLayoutParams();
        paramsTextFormat.height = calculateMenuMargin();
        ViewGroup.LayoutParams paramsDrawPanel = mDrawLayout.getLayoutParams();
        paramsDrawPanel.height = calculateMenuMargin();

        // Create DatabaseHandler
        dbHandler = new DatabaseHandler(getApplicationContext());

        // Get default spannable value
        spannable = editText.getText();

        // get ID data from intent
        Intent intent = getIntent();
        noteID = Integer.parseInt(intent.getStringExtra("id"));

        // disable soft keyboard when editText is focused
        disableSoftInputFromAppearing(editText);

        // Auto-enable format menu panel when text is selected
        manageContextMenuBar(editText);

        // Feature Disabled
        // Auto-enable soft keyboard when activity starts
        //toggleKeyboard(null);

        // Load note
        if (noteID != -1) {
            loadNote(noteID);
        }

        // Handling drawingView's onTouchListener via EditText onTouchListener
        editText.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (drawMode) {
                    drawingView.draw(event.getX() + editText.getX(), event.getY() + editText.getY(), event.getAction());
                    return true;
                } else {
                    return false;
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Creating menu
        getMenuInflater().inflate(R.menu.menu_note, menu);
        return true;
    }

    /**
     * Method that Overrides back button behavior
     */
    @Override
    public void onBackPressed() {

        View formatTextSliderView = findViewById(R.id.formatTextSlider);
        View drawPanelSliderView = findViewById(R.id.drawPanelSlider);

        if (formatTextSliderView.getVisibility() == View.VISIBLE) {
            formatTextSliderView.setVisibility(View.GONE);
        }
        else if (drawPanelSliderView.getVisibility() == View.VISIBLE) {
            drawPanelSliderView.setVisibility(View.GONE);
        }
        else {
            saveOrUpdateNote(null);
        }
    }

    /**
     * Method used to toggle format text panel
     *
     * @param item MenuItem that handles that method in .xml android:OnClick
     */
    public void toggleTextFormatMenu(@Nullable MenuItem item) {

        View formatTextSliderView = findViewById(R.id.formatTextSlider);
        View drawPanelSliderView = findViewById(R.id.drawPanelSlider);

        if (formatTextSliderView.getVisibility() == View.VISIBLE) {
            formatTextSliderView.setVisibility(View.GONE);
        } else {
            if (drawPanelSliderView.getVisibility() == View.VISIBLE) {
                drawPanelSliderView.setVisibility(View.GONE);
            }
            formatTextSliderView.setVisibility(View.VISIBLE);
        }

        // After changes:
        drawMode = formatTextSliderView.getVisibility() != View.VISIBLE
                && drawPanelSliderView.getVisibility() == View.VISIBLE;
    }

    /**
     * Method used to toggle draw menu panel
     *
     * @param item MenuItem that handles that method in .xml android:OnClick
     */
    public void toggleDrawMenu(@Nullable MenuItem item) {

        View formatTextSliderView = findViewById(R.id.formatTextSlider);
        View drawPanelSliderView = findViewById(R.id.drawPanelSlider);

        if (drawPanelSliderView.getVisibility() == View.VISIBLE) {
            drawPanelSliderView.setVisibility(View.GONE);
        } else {
            if (formatTextSliderView.getVisibility() == View.VISIBLE) {
                formatTextSliderView.setVisibility(View.GONE);
            }
            hideSoftKeyboard();
            drawPanelSliderView.setVisibility(View.VISIBLE);
        }

        // After changes:
        drawMode = drawPanelSliderView.getVisibility() == View.VISIBLE;
    }

    /**
     * Method that calculates space left for EditText when format text panel is Visible
     *
     * @return Screen independent pixel count of space for EditText
     */
    private int calculateMenuMargin() {
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int height = size.y;
        return (int) Math.round(height * MENU_MARGIN_RELATIVE_MODIFIER);
    }

    /**
     * Method used to toggle soft keyboard
     *
     * @param item MenuItem that handles that method in .xml android:OnClick
     */
    public void toggleKeyboard(@Nullable MenuItem item) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        if (findViewById(R.id.drawPanelSlider).getVisibility() == View.VISIBLE) {
            findViewById(R.id.drawPanelSlider).setVisibility(View.GONE);
        }
    }

    /**
     * Method used to hide keyboard
     */
    private void hideSoftKeyboard() {
        if (this.getCurrentFocus() != null) {
            try {
                InputMethodManager imm = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(this.getCurrentFocus().getApplicationWindowToken(), 0);
            } catch (RuntimeException e) {
                //ignore
            }
        }
    }

    /**
     * Method that prevents soft keyboard appear when EditText is focused
     *
     * @param editText EditText to apply changes to
     */
    private static void disableSoftInputFromAppearing(EditText editText) {
        if (Build.VERSION.SDK_INT >= 11) { //TODO: remove
            editText.setRawInputType(InputType.TYPE_CLASS_TEXT);
            editText.setTextIsSelectable(true);
        } else {
            editText.setRawInputType(InputType.TYPE_NULL);
            editText.setFocusable(true);
        }
    }

    /**
     * Method used to show format text panel when context menu is ON
     * i.e. When text is selected
     *
     * @param editText EditText to apply changes to
     */
    private void manageContextMenuBar(EditText editText) {

        editText.setCustomSelectionActionModeCallback(new ActionMode.Callback() {

            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return true;
            }

            public void onDestroyActionMode(ActionMode mode) {
                if (findViewById(R.id.formatTextSlider).getVisibility() == View.VISIBLE) {
                    findViewById(R.id.formatTextSlider).setVisibility(View.GONE);
                }
            }

            public boolean onCreateActionMode(ActionMode mode, Menu menu) {

                if (findViewById(R.id.formatTextSlider).getVisibility() == View.GONE) {
                    findViewById(R.id.formatTextSlider).setVisibility(View.VISIBLE);
                }
                return true;
            }

            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return false;
            }
        });
    }

    /**
     * Method used to format selected text by modifying Spannable object
     *
     * @param view that handles that method in .xml android:OnClick
     */
    public void formatTextActionPerformed(View view) {

        EditText editTextLocal = (EditText) findViewById(R.id.editText);
        spannable = editTextLocal.getText();

        int posStart = editTextLocal.getSelectionStart();
        int posEnd = editTextLocal.getSelectionEnd();

        if (view.getTag().toString().equals("bold")) {
            spannable.setSpan(new StyleSpan(Typeface.BOLD), posStart, posEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (view.getTag().toString().equals("italic")) {
            spannable.setSpan(new StyleSpan(Typeface.ITALIC), posStart, posEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (view.getTag().toString().equals("underline")) {
            spannable.setSpan(new UnderlineSpan(), posStart, posEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (view.getTag().toString().equals("textBlack")) {
            spannable.setSpan(new ForegroundColorSpan(Color.BLACK), posStart, posEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (view.getTag().toString().equals("textRed")) {
            spannable.setSpan(new ForegroundColorSpan(Color.RED), posStart, posEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (view.getTag().toString().equals("textBlue")) {
            spannable.setSpan(new ForegroundColorSpan(Color.BLUE), posStart, posEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (view.getTag().toString().equals("textGreen")) {
            spannable.setSpan(new ForegroundColorSpan(Color.GREEN), posStart, posEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (view.getTag().toString().equals("textYellow")) {
            spannable.setSpan(new ForegroundColorSpan(Color.YELLOW), posStart, posEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        editTextLocal.setText(spannable);
    }

    /**
     * Method used to add note text to EditText
     *
     * @param noteID ID number of the Note entry in the SQLite database
     */
    private void loadNote(int noteID) {

        try {
            Note n = dbHandler.getNote(noteID);
            //todo fix
            editText.setText(n.getSpannable());
            editText.setSelection(editText.getText().toString().length());
            noteTitle.setText(n.getTitle());
            drawingView.setBitmap(n.getImage());
        }catch (SQLiteException e){
            e.printStackTrace();
        }
    }

    /**
     * Method used for Saving/Updating/Deleting note with special conditions
     * Handled by "Done button"
     */
    public void saveOrUpdateNote(@Nullable MenuItem menu) {

        // Don't save note if its blank
        if (editText.getText().toString().isEmpty()
        && noteTitle.getText().toString().isEmpty()
        && drawingView.isBlank()) {
            finish();
            return;
        }

        spannable = editText.getText();
        String title = noteTitle.getText().toString();

        final Note note = new Note();

        note.setTitle(title);
        note.setSpannable(spannable);
        note.setImage(drawingView.getCanvasBitmap());
        note.setDateUpdated(new Date());

        if (noteID != -1) {
            note.setId(noteID);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                dbHandler.saveOrUpdateNote(note);
                finish();
            }
        }).run();

        hideSoftKeyboard();
    }

    /**
     * Handle voice button click
     */
    public void speakButtonClicked(MenuItem menuItem) {
        startVoiceRecognitionActivity();
    }

    /**
     * Start the voice recognition activity.
     */
    private void startVoiceRecognitionActivity() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, R.string.voice_hint);
        startActivityForResult(intent, REQUEST_CODE);
    }

    /**
     * Handle the results from the voice recognition
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            ArrayList<String> matches = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);

            if (matches.size() > 0) {
                if (editText.getText().toString().length() == 0) {
                    editText.setText(matches.get(0));
                    editText.setSelection(editText.getText().toString().length());
                } else {
                    Spanned spanText = (SpannedString) TextUtils.concat(editText.getText(), " " + matches.get(0));
                    editText.setText(spanText);
                    editText.setSelection(editText.getText().toString().length());
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Method used to change drawing color
     */
    public void changeColor(View v) {

        if (v.getTag().toString().equals("black")) {
            drawingView.setPaintColor(Color.BLACK);
        } else if (v.getTag().toString().equals("red")) {
            drawingView.setPaintColor(Color.RED);
        } else if (v.getTag().toString().equals("blue")) {
            drawingView.setPaintColor(Color.BLUE);
        } else if (v.getTag().toString().equals("green")) {
            drawingView.setPaintColor(Color.GREEN);
        } else if (v.getTag().toString().equals("yellow")) {
            drawingView.setPaintColor(Color.YELLOW);
        }
    }

    /**
     * Method used to change brush size
     */
    public void changeBrushSize(View v) {

        if (v.getTag().toString().equals("small")) {
            drawingView.setBrushSize(SMALL_BRUSH);
        } else if (v.getTag().toString().equals("medium")) {
            drawingView.setBrushSize(MEDIUM_BRUSH);
        } else if (v.getTag().toString().equals("large")) {
            drawingView.setBrushSize(LARGE_BRUSH);
        }
    }

    /**
     * Method used to change erase mode
     * Handled by erase and paint button
     */
    public void eraseOrPaintMode(View v) {
        drawingView.setErase(v.getTag().toString().equals("erase"));
    }

    public void wipeCanvas(View v) {
        drawingView.startNew();
    }
}
