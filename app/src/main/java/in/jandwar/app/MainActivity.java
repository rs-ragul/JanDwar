package in.jandwar.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.ScaleAnimation;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import in.jandwar.app.ai.AiConfig;
import in.jandwar.app.ai.BhashiniGateway;
import in.jandwar.app.ai.ConversationController;
import in.jandwar.app.ai.NluExtractor;
import in.jandwar.app.ai.ProfileFragment;
import in.jandwar.app.ai.TieredNluExtractor;

public class MainActivity extends Activity {
    private static final int INDIGO = Color.rgb(27, 58, 140);
    private static final int TEAL = Color.rgb(15, 163, 163);
    private static final int SAFFRON = Color.rgb(255, 138, 31);
    private static final int PAPER = Color.rgb(247, 250, 252);
    private static final int INK = Color.rgb(24, 33, 50);
    private static final int MUTED = Color.rgb(90, 103, 124);
    private static final int VOICE_REQUEST = 42;

    private FrameLayout root;
    private JSONObject i18n;
    private JSONArray roles;
    private JSONArray centres;
    private JSONArray districts;
    private static final String SCREEN_SPLASH = "splash";
    private static final String SCREEN_LANGUAGE = "language";
    private static final String SCREEN_ONBOARDING = "onboarding";
    private static final String SCREEN_HOME = "home";
    private static final String SCREEN_SETTINGS = "settings";
    private static final String SCREEN_INTAKE = "intake";
    private static final String SCREEN_RESULTS = "results";
    private static final String SCREEN_DETAIL = "detail";
    private static final String SCREEN_COURSES = "courses";
    private static final String SCREEN_VOICE = "voice";
    private String lang = "";
    private String currentScreen = SCREEN_SPLASH;
    private int onboardingPage = 0;

    private String selectedEdu = "";
    private String selectedPref = "";
    private String selectedTravel = "";
    private String selectedDistrict = "";
    private final Set<String> selectedInterests = new HashSet<>();
    private SpeechRecognizer speechRecognizer;
    private boolean voiceActive;
    private TextView voiceTranscript;
    private TextView voiceStatus;

    // ── AI voice conversation ──────────────────────────────────────────────────
    private BhashiniGateway bhashiniGateway;
    private TieredNluExtractor nluExtractor;
    private ConversationController conversation;
    // Live conversation UI refs (populated in showConversationScreen)
    private TextView convQuestionText;
    private TextView convTranscriptText;
    private TextView convStatusText;
    private View convOrbView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        loadData();
        // Load API keys from assets/config.json
        AiConfig.load(this);
        // Initialise AI stack
        bhashiniGateway = new BhashiniGateway();
        nluExtractor = new TieredNluExtractor(bhashiniGateway);
        root = new FrameLayout(this);
        root.setBackgroundColor(PAPER);
        root.setPadding(0, getStatusBarInset(), 0, 0);
        setContentView(root);
        lang = getPrefs().getString("lang", "");
        showSplash();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop mic + TTS if user switches apps mid-conversation
        stopConversation();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopConversation();
    }

    private void showSplash() {
        LinearLayout view = pageBase(false);
        view.setGravity(Gravity.CENTER);
        ImageView logo = new ImageView(this);
        logo.setImageResource(getResources().getIdentifier("logo_premium", "drawable", getPackageName()));
        view.addView(logo, new LinearLayout.LayoutParams(dp(150), dp(150)));
        TextView name = text("JanDwar", 34, Color.WHITE, Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        view.addView(name);
        TextView tag = text("Gateway for Citizens", 17, Color.WHITE, Typeface.NORMAL);
        tag.setAlpha(0.9f);
        tag.setGravity(Gravity.CENTER);
        view.addView(tag);
        transitionTo(view);
        new Handler().postDelayed(new Runnable() {
            @Override public void run() {
                if (lang.length() == 0) showLanguage();
                else showOnboarding();
            }
        }, 850);
    }

    private void showLanguage() {
        currentScreen = SCREEN_LANGUAGE;
        LinearLayout content = pageBase(true);
        content.setPadding(dp(18), dp(18), dp(18), dp(22));
        content.addView(heroHeader("JanDwar", "Choose Language", true));
        addSpace(content, 22);

        JSONArray langs = i18n.optJSONArray("langs");
        if (langs != null) {
            for (int i = 0; i < langs.length(); i += 2) {
                LinearLayout rowLayout = new LinearLayout(this);
                rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                rowLayout.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams rowLp = wideLp();
                rowLp.setMargins(0, 0, 0, dp(16));
                rowLayout.setLayoutParams(rowLp);

                JSONArray row = langs.optJSONArray(i);
                if (row != null) rowLayout.addView(languageCard(row.optString(0), row.optString(1)), halfLp(true));

                JSONArray rowTwo = langs.optJSONArray(i + 1);
                if (rowTwo != null) rowLayout.addView(languageCard(rowTwo.optString(0), rowTwo.optString(1)), halfLp(false));
                else rowLayout.addView(new Space(this), halfLp(false));

                content.addView(rowLayout);
            }
        }
        TextView helper = text("You can change this anytime from Settings.", 14, MUTED, Typeface.BOLD);
        helper.setGravity(Gravity.CENTER);
        content.addView(helper);
        transitionTo(scroll(content));
    }

    private void showOnboarding() {
        currentScreen = SCREEN_ONBOARDING;
        LinearLayout content = pageBase(true);
        content.setPadding(dp(18), dp(18), dp(18), dp(22));
        content.addView(heroHeader(tr("name"), tr("tagline"), true));
        addSpace(content, 20);
        LinearLayout card = card();
        TextView step = pill("0" + (onboardingPage + 1), Color.WHITE, onboardingPage == 1 ? SAFFRON : TEAL, Color.TRANSPARENT);
        LinearLayout.LayoutParams stepLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stepLp.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(step, stepLp);
        addSpace(card, 12);
        TextView title = text(onTitle(onboardingPage), 27, INK, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title);
        TextView body = text(onBody(onboardingPage), 17, MUTED, Typeface.NORMAL);
        body.setGravity(Gravity.CENTER);
        body.setPadding(dp(4), dp(10), dp(4), dp(4));
        card.addView(body);
        content.addView(card);
        addSpace(content, 18);
        LinearLayout dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        for (int i = 0; i < 3; i++) {
            TextView dot = text(i == onboardingPage ? "●" : "○", 26, i == onboardingPage ? TEAL : Color.rgb(185, 195, 210), Typeface.NORMAL);
            dots.addView(dot);
        }
        content.addView(dots);
        addSpace(content, 18);
        TextView next = primary(onboardingPage == 2 ? tr("start") : tr("continue"));
        next.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (onboardingPage < 2) {
                    onboardingPage++;
                    showOnboarding();
                } else {
                    showHome();
                }
            }
        });
        content.addView(next);
        transitionTo(scroll(content));
    }

    private void showHome() {
        stopVoice();
        currentScreen = SCREEN_HOME;
        LinearLayout content = pageBase(true);
        content.setPadding(dp(18), dp(18), dp(18), dp(22));
        content.addView(appHeader(tr("name"), tr("tagline"), tr("settings"), new View.OnClickListener() {
            @Override public void onClick(View v) { showSettings(); }
        }));
        addSpace(content, 16);
        content.addView(voiceAssistantCard());
        content.addView(sectionTitle(tr("choose_path")));
        content.addView(actionCard(tr("personalized_title"), tr("personalized_sub"), tr("start_intake"), new View.OnClickListener() {
            @Override public void onClick(View v) { showIntake(); }
        }));
        content.addView(actionCard(tr("browse_title"), tr("browse_sub"), tr("browse_action"), new View.OnClickListener() {
            @Override public void onClick(View v) { showAllCourses(); }
        }));
        addSpace(content, 8);
        content.addView(statCard("516", tr("stat_roles"), "343", tr("stat_fundable")));
        content.addView(statCard("38", tr("stat_districts"), "20", tr("stat_centres")));
        TextView note = cardText(tr("honesty_note"), 16, MUTED, Typeface.NORMAL);
        content.addView(note);
        transitionTo(scroll(content));
    }

    private LinearLayout voiceAssistantCard() {
        LinearLayout card = card();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView mic = text("●", 30, Color.WHITE, Typeface.BOLD);
        mic.setGravity(Gravity.CENTER);
        mic.setBackground(oval(SAFFRON));
        row.addView(mic, new LinearLayout.LayoutParams(dp(62), dp(62)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(14), 0, 0, 0);
        copy.addView(text(tr("voice_title"), 21, INK, Typeface.BOLD));
        copy.addView(text(tr("voice_sub"), 15, MUTED, Typeface.NORMAL));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView speak = pill(tr("speak_button"), Color.WHITE, TEAL, Color.TRANSPARENT);
        addRipple(speak);
        speak.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startVoice(); }
        });
        row.addView(speak);
        card.addView(row);
        return card;
    }

    private LinearLayout actionCard(String title, String subtitle, String action, View.OnClickListener listener) {
        LinearLayout card = card();
        addRipple(card);
        card.setOnClickListener(listener);
        card.addView(text(title, 20, INK, Typeface.BOLD));
        card.addView(text(subtitle, 15, MUTED, Typeface.NORMAL));
        TextView link = text(action + "  ->", 15, TEAL, Typeface.BOLD);
        link.setPadding(0, dp(10), 0, 0);
        card.addView(link);
        return card;
    }

    private void showAllCourses() {
        currentScreen = SCREEN_COURSES;
        LinearLayout content = pageBase(true);
        content.setPadding(dp(18), dp(18), dp(18), dp(22));
        content.addView(appHeader(tr("browse_title"), tr("browse_sub"), tr("back"), new View.OnClickListener() {
            @Override public void onClick(View v) { showHome(); }
        }));
        content.addView(cardText(tr("browse_note"), 15, MUTED, Typeface.NORMAL));
        for (int i = 0; i < roles.length(); i++) {
            JSONObject obj = roles.optJSONObject(i);
            if (obj == null) continue;
            Role role = new Role(obj);
            if (!role.validName()) continue;
            final Rec rec = new Rec(role, 0, tr("reason_base"));
            LinearLayout course = courseCard(rec);
            addRipple(course);
            course.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showDetail(rec); }
            });
            content.addView(course);
        }
        transitionTo(scroll(content));
    }

    private void showSettings() {
        currentScreen = SCREEN_SETTINGS;
        LinearLayout content = pageBase(true);
        content.setPadding(dp(18), dp(18), dp(18), dp(22));
        content.addView(appHeader(tr("settings"), tr("tagline"), tr("back"), new View.OnClickListener() {
            @Override public void onClick(View v) { showHome(); }
        }));
        addSpace(content, 16);
        content.addView(text(tr("settings_sub"), 17, MUTED, Typeface.NORMAL));
        TextView language = cardText(tr("change_lang"), 20, INK, Typeface.BOLD);
        language.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showLanguage(); }
        });
        content.addView(language);
        content.addView(infoLine(tr("offline_data"), tr("offline_data_text")));
        
        // Offline AI Download Option
        TextView aiDownload = cardText(tr("offline_ai"), 20, INK, Typeface.BOLD);
        TextView aiDesc = text(tr("offline_ai_text"), 15, MUTED, Typeface.NORMAL);
        aiDesc.setPadding(dp(18), 0, dp(18), dp(16));
        aiDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Downloading offline AI model...", Toast.LENGTH_SHORT).show();
                // TODO: Trigger actual download logic for Gemma3-270M/SmolLM2
            }
        });
        content.addView(aiDownload);
        content.addView(aiDesc);

        content.addView(infoLine(tr("privacy"), tr("privacy_text")));
        content.addView(infoLine(tr("app_name_label"), "JanDwar · " + tr("tagline")));
        transitionTo(scroll(content));
    }

    private void showIntake() {
        currentScreen = SCREEN_INTAKE;
        LinearLayout content = pageBase(true);
        content.setPadding(dp(18), dp(18), dp(18), dp(22));
        content.addView(appHeader(tr("intake"), tr("start_intake"), tr("back"), new View.OnClickListener() {
            @Override public void onClick(View v) { showHome(); }
        }));
        addSpace(content, 14);
        addQuestion(content, "q_edu", new String[]{"edu_below8", "edu_8", "edu_10", "edu_12", "edu_grad"}, selectedEdu, new SelectListener() {
            @Override public void set(String key) { selectedEdu = key; }
        });
        addQuestion(content, "q_pref", new String[]{"pref_self", "pref_wage"}, selectedPref, new SelectListener() {
            @Override public void set(String key) { selectedPref = key; }
        });
        addQuestion(content, "q_travel", new String[]{"travel_local", "travel_district", "travel_any"}, selectedTravel, new SelectListener() {
            @Override public void set(String key) { selectedTravel = key; }
        });
        content.addView(sectionTitle(tr("q_dist")));
        final TextView district = text(selectedDistrict.isEmpty() ? tr("select_district") : selectedDistrict, 20, INK, Typeface.BOLD);
        district.setBackgroundResource(getResources().getIdentifier("card_bg", "drawable", getPackageName()));
        district.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams dlp = wideLp();
        dlp.setMargins(0, dp(8), 0, dp(10));
        district.setLayoutParams(dlp);
        if (android.os.Build.VERSION.SDK_INT >= 21) district.setElevation(dp(4));
        district.setGravity(Gravity.CENTER_VERTICAL);
        addRipple(district);
        district.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickDistrict(district); }
        });
        content.addView(district);
        addSpace(content, 10);
        content.addView(sectionTitle(tr("q_int")));
        String[] interests = {"dairy", "cattle", "goat", "poultry", "farming", "food", "machine", "textile", "construction", "tailor"};
        for (final String key : interests) {
            final TextView chip = chip(interest(key), selectedInterests.contains(key));
            addRipple(chip);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (selectedInterests.contains(key)) {
                        selectedInterests.remove(key);
                        chip.setTextColor(INK);
                        chip.setBackgroundResource(getResources().getIdentifier("chip_plain", "drawable", getPackageName()));
                    } else {
                        selectedInterests.add(key);
                        chip.setTextColor(INDIGO);
                        chip.setBackgroundResource(getResources().getIdentifier("chip_selected", "drawable", getPackageName()));
                    }
                }
            });
            content.addView(chip);
        }
        addSpace(content, 18);
        TextView submit = primary(tr("submit"));
        submit.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { 
                if (selectedEdu.isEmpty() || selectedPref.isEmpty() || selectedTravel.isEmpty() || selectedDistrict.isEmpty() || selectedInterests.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Please answer all questions.", Toast.LENGTH_SHORT).show();
                    return;
                }
                showResults(match()); 
            }
        });
        content.addView(submit);
        transitionTo(scroll(content));
    }

    private void showResults(List<Rec> recs) {
        currentScreen = SCREEN_RESULTS;
        LinearLayout content = pageBase(true);
        content.setPadding(dp(18), dp(18), dp(18), dp(22));
        content.addView(appHeader(tr("options"), selectedDistrict, tr("back"), new View.OnClickListener() {
            @Override public void onClick(View v) { showIntake(); }
        }));
        addSpace(content, 14);
        content.addView(searchBox(tr("found")));
        addSpace(content, 12);
        if (recs.isEmpty()) {
            content.addView(cardText(tr("no_results"), 19, INK, Typeface.BOLD));
        }
        for (final Rec rec : recs) {
            LinearLayout card = courseCard(rec);
            TextView details = primary(tr("details"));
            details.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showDetail(rec); }
            });
            card.addView(details);
            content.addView(card);
        }
        transitionTo(scroll(content));
    }

    private void showDetail(final Rec rec) {
        currentScreen = SCREEN_DETAIL;
        LinearLayout content = pageBase(true);
        content.setPadding(dp(18), dp(18), dp(18), dp(22));
        content.addView(appHeader(tr("details"), selectedDistrict, tr("back"), new View.OnClickListener() {
            @Override public void onClick(View v) { showResults(match()); }
        }));
        addSpace(content, 16);
        content.addView(text(rec.role.name, 28, INK, Typeface.BOLD));
        content.addView(text(rec.role.code + " · " + rec.role.ssc + " · " + sectorName(rec.role.sector), 16, MUTED, Typeface.BOLD));
        addSpace(content, 10);
        content.addView(infoLine(tr("course_type"), rec.role.longTerm() ? tr("long_term") : tr("short_term")));
        content.addView(infoLine(tr("outcome"), selectedPref.equals("pref_self") ? tr("pref_self") : tr("pref_wage")));
        content.addView(infoLine(tr("fundable"), rec.role.fundable() ? tr("fundable_badge") : tr("not_fundable")));
        content.addView(infoLine(tr("asset"), tr("asset_rule")));
        content.addView(infoLine(tr("why"), rec.reason));
        JSONObject centre = centreFor(selectedDistrict);
        if (centre != null) {
            LinearLayout c = card();
            c.addView(text(tr("centre"), 21, INK, Typeface.BOLD));
            c.addView(text(centre.optString("name"), 18, INK, Typeface.BOLD));
            c.addView(text(centre.optString("address"), 16, MUTED, Typeface.NORMAL));
            c.addView(text(centre.optString("trades"), 15, MUTED, Typeface.NORMAL));
            c.addView(text(centre.optString("confidence"), 13, SAFFRON, Typeface.BOLD));
            String phone = centre.optString("phone", "");
            if (phone != null && phone.length() > 3 && !"null".equals(phone)) {
                TextView call = primary(tr("call"));
                call.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
                    }
                });
                c.addView(call);
            }
            content.addView(c);
        } else {
            content.addView(cardText(tr("no_centre"), 17, SAFFRON, Typeface.BOLD));
        }
        content.addView(cardText(tr("not_claim_text"), 15, MUTED, Typeface.NORMAL));
        transitionTo(scroll(content));
    }

    private void pickDistrict(final TextView districtText) {
        final String[] names = new String[districts.length()];
        for (int i = 0; i < districts.length(); i++) names[i] = districts.optString(i);
        new AlertDialog.Builder(this)
                .setTitle(tr("q_dist"))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        selectedDistrict = names[which];
                        if (districtText != null) {
                            districtText.setText(selectedDistrict);
                        }
                    }
                })
                .show();
    }

    private void startVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, VOICE_REQUEST);
            return;
        }
        currentScreen = SCREEN_VOICE;
        showConversationScreen();
    }

    /**
     * New AI-driven conversational voice screen.
     * Builds the UI first, then starts the ConversationController which drives itself.
     */
    private void showConversationScreen() {
        LinearLayout content = pageBase(true);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(22), dp(26), dp(22), dp(28));
        content.addView(appHeader(tr("voice_intro"), tr("voice_sub"), tr("close"), new View.OnClickListener() {
            @Override public void onClick(View v) { stopConversation(); showHome(); }
        }));
        addSpace(content, 20);

        // Pulsing orb
        TextView orb = text("", 1, Color.TRANSPARENT, Typeface.NORMAL);
        orb.setBackground(roundedGradient(100, INDIGO, TEAL));
        if (android.os.Build.VERSION.SDK_INT >= 21) orb.setElevation(dp(12));
        content.addView(orb, new LinearLayout.LayoutParams(dp(160), dp(160)));
        convOrbView = orb;
        ScaleAnimation pulse = new ScaleAnimation(1f, 1.08f, 1f, 1.08f, 1, 0.5f, 1, 0.5f);
        pulse.setDuration(900);
        pulse.setRepeatMode(android.view.animation.Animation.REVERSE);
        pulse.setRepeatCount(android.view.animation.Animation.INFINITE);
        orb.startAnimation(pulse);
        addSpace(content, 18);

        // Status label (Listening… / Thinking…)
        convStatusText = text(tr("voice_listening"), 17, TEAL, Typeface.BOLD);
        convStatusText.setGravity(Gravity.CENTER);
        content.addView(convStatusText);
        addSpace(content, 10);

        // Current question
        LinearLayout qCard = card();
        convQuestionText = text("...", 21, INK, Typeface.BOLD);
        convQuestionText.setGravity(Gravity.CENTER);
        convQuestionText.setPadding(dp(4), dp(8), dp(4), dp(8));
        qCard.addView(convQuestionText);
        content.addView(qCard);

        // User's transcript
        convTranscriptText = text(tr("voice_prompt"), 16, MUTED, Typeface.NORMAL);
        convTranscriptText.setGravity(Gravity.CENTER);
        convTranscriptText.setPadding(dp(8), dp(14), dp(8), dp(14));
        content.addView(convTranscriptText, wideLp());
        addSpace(content, 16);

        // Stop button
        TextView stop = primary(tr("stop_listening"));
        stop.setBackground(roundedStroke(28, Color.rgb(210, 58, 62), Color.TRANSPARENT, 0));
        addRipple(stop);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { stopConversation(); showHome(); }
        });
        content.addView(stop);
        transitionTo(scroll(content));

        // Start conversation controller
        conversation = new ConversationController(
                this,
                lang,
                nluExtractor,
                bhashiniGateway,
                new ConversationController.Listener() {
                    @Override
                    public void onQuestion(String questionText) {
                        if (convQuestionText != null) convQuestionText.setText(questionText);
                        if (convTranscriptText != null) convTranscriptText.setText(tr("voice_prompt"));
                    }
                    @Override
                    public void onPartialTranscript(String partial) {
                        if (convTranscriptText != null) convTranscriptText.setText(partial);
                    }
                    @Override
                    public void onTranscriptResult(String full) {
                        if (convTranscriptText != null) convTranscriptText.setText(full);
                    }
                    @Override
                    public void onFieldExtracted(ProfileFragment updated) {
                        // Sync extracted profile back to our selection state
                        applyProfileFragment(updated);
                    }
                    @Override
                    public void onDone(ProfileFragment finalProfile) {
                        applyProfileFragment(finalProfile);
                        stopConversation();
                        // Speak result summary, then show results
                        speakResultSummary(finalProfile, () -> showResults(match()));
                    }
                    @Override
                    public void onStatus(String statusText) {
                        if (convStatusText != null) convStatusText.setText(statusText);
                    }
                    @Override
                    public void onSpeaking(boolean isSpeaking) {
                        if (convOrbView != null) {
                            float scale = isSpeaking ? 1.15f : 1.0f;
                            convOrbView.animate().scaleX(scale).scaleY(scale).setDuration(200).start();
                        }
                    }
                }
        );
        conversation.start();
    }

    private void stopConversation() {
        if (conversation != null) {
            conversation.stop();
            conversation = null;
        }
        // Legacy voice
        voiceActive = false;
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.cancel();
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
    }

    private void stopVoice() {
        stopConversation();
    }

    /** Apply extracted profile fields to the legacy selection state. */
    private void applyProfileFragment(ProfileFragment frag) {
        if (frag == null) return;
        if (frag.hasEdu()) {
            // Map AI edu values to app's edu keys
            switch (frag.edu) {
                case "none": case "class5": selectedEdu = "edu_below8"; break;
                case "class8":  selectedEdu = "edu_8";   break;
                case "class10": selectedEdu = "edu_10";  break;
                case "class12": selectedEdu = "edu_12";  break;
                case "graduate": selectedEdu = "edu_grad"; break;
            }
        }
        if (frag.hasPref()) {
            selectedPref = frag.preference.equals("self_employment") ? "pref_self" : "pref_wage";
        }
        if (frag.hasMobility()) {
            switch (frag.mobility) {
                case "local":    selectedTravel = "travel_local";    break;
                case "district": selectedTravel = "travel_district"; break;
                case "state":    selectedTravel = "travel_any";      break;
            }
        }
        if (frag.hasDistrict()) selectedDistrict = frag.district;
        if (frag.hasInterests()) {
            for (String i : frag.interests) selectedInterests.add(i);
        }
    }

    /** Speak the final result summary using Bhashini TTS → then run onDone. */
    private void speakResultSummary(ProfileFragment profile, Runnable onDone) {
        String summary = buildResultSummaryText(profile);
        if (bhashiniGateway != null && bhashiniGateway.isAvailable()) {
            bhashiniGateway.tts(summary, lang, new BhashiniGateway.TtsCallback() {
                @Override public void onAudio(byte[] audio) { onDone.run(); }
                @Override public void onError(String r) { onDone.run(); }
            });
        } else {
            // Use Android TTS
            final android.speech.tts.TextToSpeech[] ttsArr = {null};
            ttsArr[0] = new android.speech.tts.TextToSpeech(this, status -> {
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    ttsArr[0].speak(summary, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "summary");
                    new Handler().postDelayed(() -> {
                        ttsArr[0].shutdown();
                        onDone.run();
                    }, 3500);
                } else {
                    onDone.run();
                }
            });
        }
    }

    private String buildResultSummaryText(ProfileFragment p) {
        switch (lang) {
            case "ta": return "உங்கள் பதில்களின் அடிப்படையில் சிறந்த படிப்பு வாய்ப்புகள் கண்டறியப்பட்டுள்ளன. தயவுசெய்து முடிவுகளை பாருங்கள்.";
            case "hi": return "आपके जवाबों के आधार पर बेहतरीन कोर्स के विकल्प मिले हैं। कृपया नतीजे देखें।";
            case "te": return "మీ సమాధానాల ఆధారంగా ఉత్తమ కోర్సు ఎంపికలు కనుగొనబడ్డాయి. దయచేసి ఫలితాలు చూడండి.";
            default:   return "Based on your answers, I found some great course options. Please see the results.";
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == VOICE_REQUEST && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoice();
        }
    }

    @Override
    public void onBackPressed() {
        stopConversation(); // Stop mic + TTS if playing
        if (SCREEN_VOICE.equals(currentScreen)) {
            stopVoice();
            showHome();
        } else if (SCREEN_HOME.equals(currentScreen) || SCREEN_LANGUAGE.equals(currentScreen) || SCREEN_SPLASH.equals(currentScreen)) {
            super.onBackPressed();
        } else if (SCREEN_ONBOARDING.equals(currentScreen)) {
            if (onboardingPage > 0) { onboardingPage--; showOnboarding(); }
            else showLanguage();
        } else if (SCREEN_SETTINGS.equals(currentScreen) || SCREEN_INTAKE.equals(currentScreen) || SCREEN_COURSES.equals(currentScreen)) {
            showHome();
        } else if (SCREEN_RESULTS.equals(currentScreen)) {
            showIntake();
        } else if (SCREEN_DETAIL.equals(currentScreen)) {
            showResults(match());
        } else {
            showHome();
        }
    }

    private void parseSentence(String sentence) {
        String s = sentence == null ? "" : sentence.toLowerCase(Locale.ROOT);
        if (s.length() == 0) return;
        for (int i = 0; i < districts.length(); i++) {
            String district = districts.optString(i);
            if (s.contains(district.toLowerCase(Locale.ROOT))) selectedDistrict = district;
        }
        if (s.contains("below") || s.contains("read") || s.contains("write")) selectedEdu = "edu_below8";
        else if (s.contains("8")) selectedEdu = "edu_8";
        else if (s.contains("10")) selectedEdu = "edu_10";
        else if (s.contains("12")) selectedEdu = "edu_12";
        else if (s.contains("graduate") || s.contains("degree")) selectedEdu = "edu_grad";
        if (s.contains("business") || s.contains("own") || s.contains("self")) selectedPref = "pref_self";
        if (s.contains("job") || s.contains("employer") || s.contains("work")) selectedPref = "pref_wage";
        String[] keys = {"dairy", "cattle", "goat", "poultry", "farming", "food", "machine", "textile", "construction", "tailor"};
        for (String key : keys) {
            if (s.contains(key) || s.contains(interest(key).toLowerCase(Locale.ROOT))) selectedInterests.add(key);
        }
    }

    private List<Rec> match() {
        List<Rec> out = new ArrayList<>();
        int edu = eduRank(selectedEdu);
        for (int i = 0; i < roles.length(); i++) {
            JSONObject obj = roles.optJSONObject(i);
            if (obj == null) continue;
            Role role = new Role(obj);
            if (!role.validName()) continue;
            if (role.requiredEdu() > edu) continue;
            if (role.longTerm() && edu < 3) continue;
            int score = 40;
            String reason = tr("reason_base");
            boolean interestHit = false;
            for (String key : selectedInterests) {
                if (role.matchesInterest(key)) {
                    interestHit = true;
                    score += 80;
                }
            }
            if (!interestHit) score -= 25;
            if (role.fundable()) score += 25;
            if (selectedPref.equals("pref_self") && role.selfEmploymentFit()) score += 22;
            if (selectedPref.equals("pref_wage") && role.wageFit()) score += 22;
            if (centreFor(selectedDistrict) != null && role.sector.equals("agriculture")) score += 14;
            score += Math.max(0, 8 - role.level());
            if (interestHit) reason = tr("reason_interest") + " " + interestList() + ".";
            out.add(new Rec(role, score, reason));
        }
        Collections.sort(out, new Comparator<Rec>() {
            @Override public int compare(Rec a, Rec b) { return b.score - a.score; }
        });
        if (out.size() > 3) return new ArrayList<>(out.subList(0, 3));
        return out;
    }

    private JSONObject centreFor(String district) {
        for (int i = 0; i < centres.length(); i++) {
            JSONObject centre = centres.optJSONObject(i);
            if (centre != null && district.equalsIgnoreCase(centre.optString("district"))) return centre;
        }
        return null;
    }

    private LinearLayout heroHeader(String title, String subtitle, boolean withLogo) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER);
        header.setPadding(dp(22), dp(26), dp(22), dp(28));
        header.setBackground(roundedGradient(32, INDIGO, TEAL));
        LinearLayout.LayoutParams lp = wideLp();
        lp.setMargins(0, 0, 0, dp(6));
        header.setLayoutParams(lp);
        if (android.os.Build.VERSION.SDK_INT >= 21) header.setElevation(dp(8));

        if (withLogo) {
            ImageView logo = new ImageView(this);
            logo.setImageResource(getResources().getIdentifier("logo_premium", "drawable", getPackageName()));
            LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(74), dp(74));
            logoLp.gravity = Gravity.CENTER_HORIZONTAL;
            logoLp.bottomMargin = dp(8);
            header.addView(logo, logoLp);
        }
        TextView name = text(title, 34, Color.WHITE, Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        header.addView(name);
        TextView sub = text(subtitle, 18, Color.WHITE, Typeface.NORMAL);
        sub.setAlpha(0.92f);
        sub.setGravity(Gravity.CENTER);
        header.addView(sub);
        return header;
    }

    private LinearLayout appHeader(String title, String subtitle, String action, View.OnClickListener listener) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(18), dp(18), dp(18));
        header.setBackground(roundedGradient(28, INDIGO, TEAL));
        if (android.os.Build.VERSION.SDK_INT >= 21) header.setElevation(dp(7));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(getResources().getIdentifier("logo_premium", "drawable", getPackageName()));
        row.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setPadding(dp(12), 0, dp(8), 0);
        TextView titleText = text(title, 20, Color.WHITE, Typeface.BOLD);
        titleText.setMaxLines(2);
        titleBlock.addView(titleText);
        TextView sub = text(subtitle, 14, Color.WHITE, Typeface.NORMAL);
        sub.setAlpha(0.88f);
        titleBlock.addView(sub);
        row.addView(titleBlock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView right = pill(action, Color.WHITE, Color.argb(42, 255, 255, 255), Color.TRANSPARENT);
        right.setMaxLines(1);
        addRipple(right);
        right.setOnClickListener(listener);
        row.addView(right);
        header.addView(row);
        return header;
    }

    private LinearLayout languageCard(final String code, String label) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(8), dp(18), dp(8), dp(18));
        boolean selected = code.equals(lang);
        card.setBackground(roundedStroke(20, Color.WHITE, selected ? TEAL : Color.rgb(225, 230, 238), selected ? 3 : 1));
        if (android.os.Build.VERSION.SDK_INT >= 21) card.setElevation(selected ? dp(8) : dp(5));

        TextView main = text(label, code.equals("en") ? 24 : 27, INK, Typeface.BOLD);
        main.setGravity(Gravity.CENTER);
        main.setMaxLines(2);
        card.addView(main);
        TextView sub = text(languageEnglishName(code), 14, Color.rgb(28, 31, 38), Typeface.NORMAL);
        sub.setGravity(Gravity.CENTER);
        card.addView(sub);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                lang = code;
                getPrefs().edit().putString("lang", lang).apply();
                onboardingPage = 0;
                showOnboarding();
            }
        });
        return card;
    }

    private TextView searchBox(String label) {
        TextView search = text(tr("search") + "  ·  " + label, 15, MUTED, Typeface.NORMAL);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setPadding(dp(18), dp(14), dp(18), dp(14));
        search.setBackground(roundedStroke(22, Color.WHITE, Color.rgb(232, 236, 244), 1));
        if (android.os.Build.VERSION.SDK_INT >= 21) search.setElevation(dp(3));
        return search;
    }

    private TextView sectionTitle(String label) {
        TextView t = text(label, 20, INK, Typeface.BOLD);
        t.setPadding(dp(2), dp(14), dp(2), dp(8));
        return t;
    }

    private LinearLayout courseCard(final Rec rec) {
        LinearLayout card = card();

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.TOP);
        TextView mark = sectorMark(rec.role.sector);
        LinearLayout.LayoutParams markLp = new LinearLayout.LayoutParams(dp(56), dp(56));
        markLp.rightMargin = dp(12);
        top.addView(mark, markLp);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(rec.role.name, 18, INK, Typeface.BOLD);
        name.setMaxLines(3);
        copy.addView(name);
        copy.addView(text(rec.role.code + " · " + rec.role.ssc, 13, MUTED, Typeface.BOLD));
        top.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView badge = pill(rec.role.fundable() ? "Fundable" : "Check", Color.WHITE, rec.role.fundable() ? Color.rgb(54, 163, 82) : SAFFRON, Color.TRANSPARENT);
        top.addView(badge);
        card.addView(top);

        addSpace(card, 10);
        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.addView(metaPill(rec.role.longTerm() ? tr("long_term") : tr("short_term")), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        meta.addView(metaPill(tr("level") + " " + rec.role.levelLabel()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(meta);

        JSONObject centre = centreFor(selectedDistrict);
        String centreText = centre == null ? tr("no_centre") : centre.optString("name");
        TextView centreLine = text(centreText, 14, centre == null ? SAFFRON : MUTED, Typeface.BOLD);
        centreLine.setPadding(0, dp(8), 0, 0);
        card.addView(centreLine);
        return card;
    }

    private TextView sectorMark(String sector) {
        String label = "SK";
        int color = TEAL;
        if ("agriculture".equals(sector)) { label = "AG"; color = Color.rgb(22, 150, 84); }
        else if ("food_processing".equals(sector)) { label = "FD"; color = SAFFRON; }
        else if ("construction".equals(sector)) { label = "CN"; color = INDIGO; }
        else if ("handloom_textile".equals(sector) || "apparel".equals(sector)) { label = "TX"; color = Color.rgb(211, 42, 132); }
        TextView mark = text(label, 17, Color.WHITE, Typeface.BOLD);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(oval(color));
        return mark;
    }

    private TextView metaPill(String label) {
        TextView t = text(label, 12, MUTED, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(7), dp(8), dp(7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(2), 0, dp(4), 0);
        t.setLayoutParams(lp);
        t.setBackground(roundedStroke(14, Color.rgb(248, 250, 253), Color.rgb(229, 234, 242), 1));
        return t;
    }

    private TextView pill(String label, int textColor, int fill, int stroke) {
        TextView t = text(label, 13, textColor, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(7), dp(12), dp(7));
        t.setBackground(roundedStroke(18, fill, stroke, stroke == Color.TRANSPARENT ? 0 : 1));
        return t;
    }

    private GradientDrawable roundedGradient(int radiusDp, int start, int end) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable roundedStroke(int radiusDp, int fill, int stroke, int strokeWidthDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeWidthDp > 0) drawable.setStroke(dp(strokeWidthDp), stroke);
        return drawable;
    }

    private GradientDrawable oval(int fill) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);
        return drawable;
    }

    private LinearLayout.LayoutParams halfLp(boolean left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(124), 1);
        if (left) lp.setMargins(0, 0, dp(8), 0);
        else lp.setMargins(dp(8), 0, 0, 0);
        return lp;
    }

    private void addQuestion(LinearLayout content, String title, final String[] keys, String selected, final SelectListener listener) {
        content.addView(sectionTitle(tr(title)));
        final List<TextView> chips = new ArrayList<>();
        for (final String key : keys) {
            final TextView item = chip(tr(key), key.equals(selected));
            addRipple(item);
            chips.add(item);
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    listener.set(key);
                    for (int i = 0; i < keys.length; i++) {
                        boolean isSelected = keys[i].equals(key);
                        chips.get(i).setTextColor(isSelected ? INDIGO : INK);
                        chips.get(i).setBackgroundResource(getResources().getIdentifier(isSelected ? "chip_selected" : "chip_plain", "drawable", getPackageName()));
                    }
                }
            });
            content.addView(item);
        }
        addSpace(content, 10);
    }

    private LinearLayout topBar(String title, String action, View.OnClickListener listener) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView left = text(title, 22, INK, Typeface.BOLD);
        bar.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView right = text(action, 15, TEAL, Typeface.BOLD);
        right.setGravity(Gravity.CENTER);
        right.setPadding(dp(10), dp(10), dp(10), dp(10));
        right.setOnClickListener(listener);
        bar.addView(right);
        return bar;
    }

    private LinearLayout statCard(String a, String al, String b, String bl) {
        LinearLayout card = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(statBlock(a, al), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(statBlock(b, bl), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(row);
        return card;
    }

    private LinearLayout statBlock(String number, String label) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.addView(text(number, 28, INDIGO, Typeface.BOLD));
        TextView l = text(label, 14, MUTED, Typeface.BOLD);
        l.setGravity(Gravity.CENTER);
        box.addView(l);
        return box;
    }

    private LinearLayout infoLine(String label, String value) {
        LinearLayout line = card();
        line.addView(text(label, 15, TEAL, Typeface.BOLD));
        line.addView(text(value, 17, INK, Typeface.NORMAL));
        return line;
    }

    private LinearLayout pageBase(boolean light) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(24), dp(18), dp(24));
        if (!light) content.setBackgroundResource(getResources().getIdentifier("splash_bg", "drawable", getPackageName()));
        else content.setBackgroundColor(PAPER);
        return content;
    }

    private ScrollView scroll(View child) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(child);
        return scroll;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(getResources().getIdentifier("card_bg", "drawable", getPackageName()));
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        LinearLayout.LayoutParams lp = wideLp();
        lp.setMargins(0, dp(8), 0, dp(12));
        card.setLayoutParams(lp);
        if (android.os.Build.VERSION.SDK_INT >= 21) card.setElevation(dp(4));
        return card;
    }

    private TextView cardText(String value, int sp, int color, int style) {
        TextView t = text(value, sp, color, style);
        t.setBackgroundResource(getResources().getIdentifier("card_bg", "drawable", getPackageName()));
        t.setPadding(dp(20), dp(20), dp(20), dp(20));
        LinearLayout.LayoutParams lp = wideLp();
        lp.setMargins(0, dp(8), 0, dp(12));
        t.setLayoutParams(lp);
        if (android.os.Build.VERSION.SDK_INT >= 21) t.setElevation(dp(4));
        return t;
    }

    private TextView primary(String label) {
        TextView t = text(label, 20, Color.WHITE, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(getResources().getIdentifier("button_primary", "drawable", getPackageName()));
        t.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams lp = wideLp();
        lp.setMargins(0, dp(12), 0, dp(12));
        t.setLayoutParams(lp);
        addRipple(t);
        return t;
    }

    private TextView chip(String label, boolean selected) {
        TextView t = text(label, 17, selected ? INDIGO : INK, Typeface.BOLD);
        t.setBackgroundResource(getResources().getIdentifier(selected ? "chip_selected" : "chip_plain", "drawable", getPackageName()));
        t.setPadding(dp(16), dp(13), dp(16), dp(13));
        LinearLayout.LayoutParams lp = wideLp();
        lp.setMargins(0, dp(5), 0, dp(5));
        t.setLayoutParams(lp);
        return t;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, style);
        t.setLineSpacing(dp(2), 1.05f);
        t.setIncludeFontPadding(true);
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            t.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY);
            t.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NORMAL);
        }
        return t;
    }

    private LinearLayout.LayoutParams wideLp() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void addSpace(LinearLayout layout, int dp) {
        Space s = new Space(this);
        layout.addView(s, new LinearLayout.LayoutParams(1, dp(dp)));
    }

    private void transitionTo(final View next) {
        next.setAlpha(0f);
        next.setTranslationY(dp(16));
        root.removeAllViews();
        root.addView(next, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        next.animate().alpha(1f).translationY(0).setDuration(320).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void loadData() {
        try {
            i18n = new JSONObject(readAsset("i18n.json"));
            roles = new JSONArray(readAsset("job_roles.json"));
            centres = new JSONArray(readAsset("centres.json"));
            JSONObject districtObj = new JSONObject(readAsset("districts.json"));
            districts = districtObj.getJSONArray("all");
        } catch (Exception e) {
            i18n = new JSONObject();
            roles = new JSONArray();
            centres = new JSONArray();
            districts = new JSONArray();
            Toast.makeText(this, "Data loading failed", Toast.LENGTH_LONG).show();
        }
    }

    private String readAsset(String name) throws Exception {
        InputStream is = getAssets().open(name);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = is.read(buffer)) != -1) os.write(buffer, 0, read);
        is.close();
        return os.toString("UTF-8");
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences("jandwar", MODE_PRIVATE);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int getStatusBarInset() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resourceId == 0 ? dp(24) : getResources().getDimensionPixelSize(resourceId);
    }

    private String tr(String key) {
        if ("name".equals(key)) return "JanDwar";
        String extra = extra(key);
        if (extra.length() > 0) return extra;
        JSONObject strings = i18n.optJSONObject("strings");
        JSONObject local = strings == null ? null : strings.optJSONObject(lang.length() == 0 ? "en" : lang);
        if (local != null && local.has(key)) return local.optString(key);
        JSONObject en = strings == null ? null : strings.optJSONObject("en");
        if (en != null && en.has(key)) return en.optString(key);
        return key;
    }

    private String interest(String key) {
        JSONObject all = i18n.optJSONObject("interest");
        JSONObject local = all == null ? null : all.optJSONObject(lang.length() == 0 ? "en" : lang);
        if (local != null && local.has(key)) return local.optString(key);
        JSONObject en = all == null ? null : all.optJSONObject("en");
        return en == null ? key : en.optString(key, key);
    }

    private String extra(String key) {
        Map<String, String> en = new HashMap<>();
        en.put("tagline", "Gateway for Citizens");
        en.put("continue", "Continue");
        en.put("start", "Start");
        en.put("back", "Back");
        en.put("home_title", "Find the right livelihood path");
        en.put("home_sub", "Answer a few simple questions. JanDwar works offline and shows only verified facts.");
        en.put("stat_roles", "NSQF roles");
        en.put("stat_fundable", "fundable domains");
        en.put("stat_districts", "TN districts");
        en.put("stat_centres", "verified centres");
        en.put("start_intake", "Find my options");
        en.put("search", "Search");
        en.put("settings", "Settings");
        en.put("settings_sub", "Keep the app simple, private and ready for village use.");
        en.put("select_district", "Select District");
        en.put("offline_data", "Offline data");
        en.put("offline_data_text", "Job roles, districts and centre details are stored inside the app.");
        en.put("offline_ai", "Download Offline AI (45MB)");
        en.put("offline_ai_text", "Get the small local AI model to use voice without internet.");
        en.put("privacy", "Privacy");
        en.put("privacy_text", "No personal details are saved or uploaded in this prototype.");
        en.put("app_name_label", "App");
        en.put("honesty_note", "JanDwar never invents a course, centre, fee or subsidy. If local centre data is missing, it tells you clearly.");
        en.put("intake", "Your details");
        en.put("mic_label", "Mic");
        en.put("speak_button", "Speak now");
        en.put("details", "View details");
        en.put("level", "Level");
        en.put("short_term", "Short-term");
        en.put("long_term", "Long-term");
        en.put("asset_rule", "Up to Rs.50,000 or 50% of asset cost with loan, whichever is lower.");
        en.put("not_claim_text", "Confirm final eligibility, fee, batch date and empanelment with TAHDCO or the training centre before enrolment.");
        en.put("call", "Call centre");
        en.put("no_results", "No safe match found. Try Class 10 or another interest.");
        en.put("reason_base", "Matched using education, interest, scheme rules and centre availability.");
        en.put("reason_interest", "Matches your interest in");
        en.put("on_0", "Use big taps or one spoken sentence. No form stress.");
        en.put("on_1", "Recommendations follow PM-AJAY rules and education gates.");
        en.put("on_2", "Centres are shown only when verified for your district.");
        en.put("voice_intro", "Meet your voice assistant");
        en.put("voice_title", "Tell JanDwar what you need");
        en.put("voice_sub", "Speak naturally. We will find a useful starting point.");
        en.put("choose_path", "Choose your path");
        en.put("personalized_title", "Personalized course finder");
        en.put("personalized_sub", "Answer three simple questions and get matched options.");
        en.put("browse_title", "Browse all courses");
        en.put("browse_sub", "Explore every course in the offline catalogue.");
        en.put("browse_action", "See all courses");
        en.put("browse_note", "516 qualification packs are bundled in this app. Tap a course for its details.");
        en.put("offline_search", "Offline search");
        en.put("offline_search_sub", "Tap the choices below. No internet or account is needed.");
        en.put("close", "Close");
        en.put("voice_listening", "Listening");
        en.put("voice_hearing", "I am listening to you");
        en.put("voice_prompt", "Speak naturally. Tap stop when you are finished.");
        en.put("stop_listening", "Stop listening");
        en.put("voice_unavailable", "Voice input is not available on this phone.");

        if ("en".equals(lang) || lang.length() == 0) return en.getOrDefault(key, "");
        Map<String, String> local = new HashMap<>(en);
        addFlowTranslations(local);
        if ("ta".equals(lang)) {
            local.put("tagline", "மக்களுக்கான நுழைவாயில்");
            local.put("continue", "தொடரவும்");
            local.put("start", "தொடங்கு");
            local.put("back", "பின்");
            local.put("home_title", "சரியான வாழ்வாதார பாதையை கண்டறியுங்கள்");
            local.put("home_sub", "சில எளிய கேள்விகளுக்கு பதில் அளிக்கவும். JanDwar ஆஃப்லைனில் வேலை செய்கிறது.");
            local.put("stat_roles", "NSQF பணிகள்");
            local.put("stat_fundable", "நிதியுதவி துறைகள்");
            local.put("stat_districts", "தமிழ்நாடு மாவட்டங்கள்");
            local.put("stat_centres", "சரிபார்க்கப்பட்ட மையங்கள்");
            local.put("start_intake", "என் வாய்ப்புகளை கண்டறி");
            local.put("search", "தேடு");
            local.put("settings", "அமைப்புகள்");
            local.put("settings_sub", "கிராம பயன்பாட்டிற்கு எளிமையாகவும் தனியுரிமையுடனும் வைத்திருக்கவும்.");
            local.put("offline_data", "ஆஃப்லைன் தரவு");
            local.put("offline_data_text", "பணிகள், மாவட்டங்கள், மைய விவரங்கள் செயலிக்குள் சேமிக்கப்பட்டுள்ளன.");
            local.put("offline_ai", "ஆஃப்லைன் AI பதிவிறக்கம் (45MB)");
            local.put("offline_ai_text", "இணையம் இல்லாமல் குரலைப் பயன்படுத்த சிறிய உள் மாடலைப் பெறுங்கள்.");
            local.put("privacy", "தனியுரிமை");
            local.put("privacy_text", "இந்த முன்மாதிரியில் தனிப்பட்ட விவரங்கள் சேமிக்கப்படவோ பதிவேற்றப்படவோ இல்லை.");
            local.put("app_name_label", "செயலி");
            local.put("honesty_note", "JanDwar பாடம், மையம், கட்டணம் அல்லது உதவித்தொகையை கற்பனை செய்து காட்டாது. தகவல் இல்லை என்றால் தெளிவாக சொல்வது.");
            local.put("intake", "உங்கள் விவரங்கள்");
            local.put("mic_label", "மைக்");
            local.put("speak_button", "இப்போது பேசுங்கள்");
            local.put("details", "விவரம் பார்க்க");
            local.put("level", "நிலை");
            local.put("short_term", "குறுகிய காலம்");
            local.put("long_term", "நீண்ட காலம்");
            local.put("asset_rule", "கடனுடன் சொத்து செலவின் 50% அல்லது ரூ.50,000 வரை, எது குறைவோ அது.");
            local.put("not_claim_text", "இறுதி தகுதி, கட்டணம், வகுப்பு தேதி மற்றும் அங்கீகாரத்தை TAHDCO அல்லது பயிற்சி மையத்தில் உறுதி செய்யவும்.");
            local.put("call", "மையத்தை அழை");
            local.put("no_results", "பாதுகாப்பான பொருத்தம் கிடைக்கவில்லை. 10-ம் வகுப்பு அல்லது வேறு ஆர்வத்தை முயற்சிக்கவும்.");
            local.put("reason_base", "கல்வி, ஆர்வம், திட்ட விதிகள் மற்றும் மையத் தகவலின் அடிப்படையில் பொருத்தப்பட்டது.");
            local.put("reason_interest", "உங்கள் ஆர்வத்துடன் பொருந்துகிறது:");
            local.put("on_0", "பெரிய பொத்தான்களைத் தட்டுங்கள் அல்லது ஒரு வாக்கியம் பேசுங்கள். படிவ சிரமம் இல்லை.");
            local.put("on_1", "பரிந்துரைகள் PM-AJAY விதிகள் மற்றும் கல்வித் தகுதியை பின்பற்றும்.");
            local.put("on_2", "உங்கள் மாவட்டத்திற்கு சரிபார்க்கப்பட்ட மையம் இருந்தால் மட்டுமே காட்டப்படும்.");
        } else if ("hi".equals(lang)) {
            local.put("tagline", "नागरिकों का प्रवेश द्वार");
            local.put("continue", "जारी रखें");
            local.put("start", "शुरू करें");
            local.put("back", "वापस");
            local.put("home_title", "सही आजीविका रास्ता खोजें");
            local.put("home_sub", "कुछ सरल प्रश्नों का उत्तर दें। JanDwar ऑफलाइन काम करता है और सत्यापित तथ्य दिखाता है।");
            local.put("stat_roles", "NSQF भूमिकाएं");
            local.put("stat_fundable", "वित्तपोष्य क्षेत्र");
            local.put("stat_districts", "तमिलनाडु जिले");
            local.put("stat_centres", "सत्यापित केंद्र");
            local.put("start_intake", "मेरे विकल्प खोजें");
            local.put("search", "खोजें");
            local.put("settings", "सेटिंग्स");
            local.put("settings_sub", "ऐप को सरल, निजी और गांव के उपयोग के लिए तैयार रखें।");
            local.put("offline_data", "ऑफलाइन डेटा");
            local.put("offline_data_text", "भूमिकाएं, जिले और केंद्र विवरण ऐप के अंदर रखे गए हैं।");
            local.put("offline_ai", "ऑफलाइन AI डाउनलोड (45MB)");
            local.put("offline_ai_text", "बिना इंटरनेट आवाज़ का उपयोग करने के लिए छोटा लोकल मॉडल डाउनलोड करें।");
            local.put("privacy", "गोपनीयता");
            local.put("privacy_text", "इस प्रोटोटाइप में निजी जानकारी सेव या अपलोड नहीं होती।");
            local.put("app_name_label", "ऐप");
            local.put("honesty_note", "JanDwar कोर्स, केंद्र, शुल्क या सहायता राशि नहीं गढ़ता। डेटा न हो तो साफ बताता है।");
            local.put("intake", "आपकी जानकारी");
            local.put("mic_label", "माइक");
            local.put("speak_button", "अब बोलिए");
            local.put("details", "विवरण देखें");
            local.put("level", "स्तर");
            local.put("short_term", "अल्पकालिक");
            local.put("long_term", "दीर्घकालिक");
            local.put("asset_rule", "ऋण के साथ संपत्ति लागत का 50% या रु.50,000 तक, जो कम हो।");
            local.put("not_claim_text", "नामांकन से पहले अंतिम पात्रता, शुल्क, बैच तिथि और मान्यता TAHDCO या केंद्र से पुष्टि करें।");
            local.put("call", "केंद्र को कॉल करें");
            local.put("no_results", "सुरक्षित मिलान नहीं मिला। 10वीं या दूसरी रुचि आज़माएं।");
            local.put("reason_base", "शिक्षा, रुचि, योजना नियम और केंद्र उपलब्धता से मिलान किया गया।");
            local.put("reason_interest", "आपकी रुचि से मेल खाता है:");
            local.put("on_0", "बड़े बटन दबाएं या एक वाक्य बोलें। फॉर्म की परेशानी नहीं।");
            local.put("on_1", "सुझाव PM-AJAY नियमों और शिक्षा पात्रता का पालन करते हैं।");
            local.put("on_2", "केंद्र तभी दिखते हैं जब वे आपके जिले के लिए सत्यापित हों।");
        } else if ("te".equals(lang)) {
            local.put("tagline", "పౌరుల కోసం ద్వారం");
            local.put("continue", "కొనసాగించు");
            local.put("start", "ప్రారంభించు");
            local.put("back", "వెనుకకు");
            local.put("home_title", "సరైన జీవనోపాధి మార్గం కనుగొనండి");
            local.put("home_sub", "కొన్ని సులభమైన ప్రశ్నలకు సమాధానం ఇవ్వండి. JanDwar ఆఫ్లైన్లో పని చేసి ధృవీకరించిన విషయాలు చూపుతుంది.");
            local.put("stat_roles", "NSQF పాత్రలు");
            local.put("stat_fundable", "నిధి రంగాలు");
            local.put("stat_districts", "తమిళనాడు జిల్లాలు");
            local.put("stat_centres", "ధృవీకరించిన కేంద్రాలు");
            local.put("start_intake", "నా ఎంపికలు కనుగొను");
            local.put("search", "వెతకండి");
            local.put("settings", "సెట్టింగ్స్");
            local.put("settings_sub", "యాప్‌ను సులభంగా, ప్రైవేట్‌గా, గ్రామ వినియోగానికి సిద్ధంగా ఉంచండి.");
            local.put("offline_data", "ఆఫ్లైన్ డేటా");
            local.put("offline_data_text", "పాత్రలు, జిల్లాలు, కేంద్ర వివరాలు యాప్‌లోనే నిల్వ ఉన్నాయి.");
            local.put("offline_ai", "ఆఫ్‌లైన్ AI డౌన్‌లోడ్ (45MB)");
            local.put("offline_ai_text", "ఇంటర్నెట్ లేకుండా వాయిస్‌ని ఉపయోగించడానికి చిన్న లోకల్ మోడల్‌ను పొందండి.");
            local.put("privacy", "గోప్యత");
            local.put("privacy_text", "ఈ నమూనాలో వ్యక్తిగత వివరాలు సేవ్ లేదా అప్లోడ్ చేయబడవు.");
            local.put("app_name_label", "యాప్");
            local.put("honesty_note", "JanDwar కోర్సు, కేంద్రం, ఫీజు లేదా సబ్సిడీని ఊహించి చూపదు. సమాచారం లేకపోతే స్పష్టంగా చెబుతుంది.");
            local.put("intake", "మీ వివరాలు");
            local.put("mic_label", "మైక్");
            local.put("speak_button", "ఇప్పుడు మాట్లాడండి");
            local.put("details", "వివరాలు");
            local.put("level", "స్థాయి");
            local.put("short_term", "తక్కువ కాలం");
            local.put("long_term", "దీర్ఘకాలం");
            local.put("asset_rule", "రుణంతో ఆస్తి ఖర్చులో 50% లేదా రూ.50,000 వరకు, ఏది తక్కువైతే అది.");
            local.put("not_claim_text", "చేరే ముందు తుది అర్హత, ఫీజు, బ్యాచ్ తేదీ, గుర్తింపును TAHDCO లేదా కేంద్రంతో నిర్ధారించండి.");
            local.put("call", "కేంద్రానికి కాల్ చేయండి");
            local.put("no_results", "సురక్షిత సరిపోలిక దొరకలేదు. 10వ తరగతి లేదా మరో ఆసక్తిని ప్రయత్నించండి.");
            local.put("reason_base", "విద్య, ఆసక్తి, పథక నియమాలు మరియు కేంద్ర లభ్యతతో సరిపోల్చబడింది.");
            local.put("reason_interest", "మీ ఆసక్తికి సరిపోతుంది:");
            local.put("on_0", "పెద్ద బటన్లు నొక్కండి లేదా ఒక వాక్యం మాట్లాడండి. ఫారం ఇబ్బంది లేదు.");
            local.put("on_1", "సిఫార్సులు PM-AJAY నియమాలు మరియు విద్య అర్హతను అనుసరిస్తాయి.");
            local.put("on_2", "మీ జిల్లాకు ధృవీకరించిన కేంద్రం ఉన్నప్పుడే చూపుతుంది.");
        } else if ("kn".equals(lang)) {
            local.put("tagline", "ನಾಗರಿಕರ ದ್ವಾರ");
            local.put("continue", "ಮುಂದುವರಿಸಿ");
            local.put("start", "ಪ್ರಾರಂಭಿಸಿ");
            local.put("back", "ಹಿಂದೆ");
            local.put("home_title", "ಸರಿಯಾದ ಜೀವನೋಪಾಯ ದಾರಿ ಹುಡುಕಿ");
            local.put("home_sub", "ಕೆಲವು ಸರಳ ಪ್ರಶ್ನೆಗಳಿಗೆ ಉತ್ತರಿಸಿ. JanDwar ಆಫ್ಲೈನ್ ಕೆಲಸ ಮಾಡುತ್ತದೆ ಮತ್ತು ಪರಿಶೀಲಿತ ಮಾಹಿತಿಯನ್ನು ಮಾತ್ರ ತೋರಿಸುತ್ತದೆ.");
            local.put("stat_roles", "NSQF ಪಾತ್ರಗಳು");
            local.put("stat_fundable", "ಅನುದಾನ ಕ್ಷೇತ್ರಗಳು");
            local.put("stat_districts", "ತಮಿಳುನಾಡು ಜಿಲ್ಲೆಗಳು");
            local.put("stat_centres", "ಪರಿಶೀಲಿತ ಕೇಂದ್ರಗಳು");
            local.put("start_intake", "ನನ್ನ ಆಯ್ಕೆ ಹುಡುಕಿ");
            local.put("search", "ಹುಡುಕಿ");
            local.put("settings", "ಸೆಟ್ಟಿಂಗ್‌ಗಳು");
            local.put("settings_sub", "ಆಪ್ ಅನ್ನು ಸರಳ, ಖಾಸಗಿ ಮತ್ತು ಗ್ರಾಮ ಬಳಕೆಗೆ ಸಿದ್ಧವಾಗಿರಿಸಿ.");
            local.put("offline_data", "ಆಫ್ಲೈನ್ ಡೇಟಾ");
            local.put("offline_data_text", "ಪಾತ್ರಗಳು, ಜಿಲ್ಲೆಗಳು ಮತ್ತು ಕೇಂದ್ರ ವಿವರಗಳು ಆಪ್ ಒಳಗೆ ಸಂಗ್ರಹವಾಗಿವೆ.");
            local.put("offline_ai", "ಆಫ್‌ಲೈನ್ AI ಡೌನ್‌ಲೋಡ್ (45MB)");
            local.put("offline_ai_text", "ಇಂಟರ್ನೆಟ್ ಇಲ್ಲದೆ ಧ್ವನಿಯನ್ನು ಬಳಸಲು ಸಣ್ಣ ಸ್ಥಳೀಯ ಮಾದರಿಯನ್ನು ಪಡೆಯಿರಿ.");
            local.put("privacy", "ಗೌಪ್ಯತೆ");
            local.put("privacy_text", "ಈ ಮಾದರಿಯಲ್ಲಿ ವೈಯಕ್ತಿಕ ವಿವರಗಳನ್ನು ಉಳಿಸಲಾಗುವುದಿಲ್ಲ ಅಥವಾ ಅಪ್‌ಲೋಡ್ ಮಾಡಲಾಗುವುದಿಲ್ಲ.");
            local.put("app_name_label", "ಆಪ್");
            local.put("honesty_note", "JanDwar ಕೋರ್ಸ್, ಕೇಂದ್ರ, ಶುಲ್ಕ ಅಥವಾ ಸಹಾಯಧನವನ್ನು ಕಲ್ಪಿಸಿ ತೋರಿಸುವುದಿಲ್ಲ. ಮಾಹಿತಿ ಇಲ್ಲದಿದ್ದರೆ ಸ್ಪಷ್ಟವಾಗಿ ಹೇಳುತ್ತದೆ.");
            local.put("intake", "ನಿಮ್ಮ ವಿವರಗಳು");
            local.put("mic_label", "ಮೈಕ್");
            local.put("speak_button", "ಈಗ ಮಾತನಾಡಿ");
            local.put("details", "ವಿವರ ನೋಡಿ");
            local.put("level", "ಮಟ್ಟ");
            local.put("short_term", "ಕಡಿಮೆ ಅವಧಿ");
            local.put("long_term", "ದೀರ್ಘ ಅವಧಿ");
            local.put("asset_rule", "ಸಾಲದೊಂದಿಗೆ ಆಸ್ತಿ ವೆಚ್ಚದ 50% ಅಥವಾ ರೂ.50,000 ವರೆಗೆ, ಯಾವುದು ಕಡಿಮೆಯೋ ಅದು.");
            local.put("not_claim_text", "ನೋಂದಣಿಗೆ ಮೊದಲು ಅಂತಿಮ ಅರ್ಹತೆ, ಶುಲ್ಕ, ಬ್ಯಾಚ್ ದಿನಾಂಕ ಮತ್ತು ಮಾನ್ಯತೆಯನ್ನು TAHDCO ಅಥವಾ ಕೇಂದ್ರದಲ್ಲಿ ಖಚಿತಪಡಿಸಿ.");
            local.put("call", "ಕೇಂದ್ರಕ್ಕೆ ಕರೆ ಮಾಡಿ");
            local.put("no_results", "ಸುರಕ್ಷಿತ ಹೊಂದಾಣಿಕೆ ಸಿಗಲಿಲ್ಲ. 10ನೇ ತರಗತಿ ಅಥವಾ ಬೇರೆ ಆಸಕ್ತಿ ಪ್ರಯತ್ನಿಸಿ.");
            local.put("reason_base", "ಶಿಕ್ಷಣ, ಆಸಕ್ತಿ, ಯೋಜನೆ ನಿಯಮಗಳು ಮತ್ತು ಕೇಂದ್ರ ಲಭ್ಯತೆಯಿಂದ ಹೊಂದಿಸಲಾಗಿದೆ.");
            local.put("reason_interest", "ನಿಮ್ಮ ಆಸಕ್ತಿಗೆ ಹೊಂದಿದೆ:");
            local.put("on_0", "ದೊಡ್ಡ ಬಟನ್‌ಗಳನ್ನು ಒತ್ತಿ ಅಥವಾ ಒಂದು ವಾಕ್ಯ ಮಾತನಾಡಿ. ಫಾರ್ಮ್ ತೊಂದರೆ ಇಲ್ಲ.");
            local.put("on_1", "ಶಿಫಾರಸುಗಳು PM-AJAY ನಿಯಮಗಳು ಮತ್ತು ಶಿಕ್ಷಣ ಅರ್ಹತೆಯನ್ನು ಅನುಸರಿಸುತ್ತವೆ.");
            local.put("on_2", "ನಿಮ್ಮ ಜಿಲ್ಲೆಗೆ ಪರಿಶೀಲಿತ ಕೇಂದ್ರ ಇದ್ದರೆ ಮಾತ್ರ ತೋರಿಸಲಾಗುತ್ತದೆ.");
        } else if ("ml".equals(lang)) {
            local.put("tagline", "പൗരന്മാർക്കുള്ള കവാടം");
            local.put("continue", "തുടരുക");
            local.put("start", "തുടങ്ങുക");
            local.put("back", "പിന്നോട്ട്");
            local.put("home_title", "ശരിയായ ഉപജീവന വഴി കണ്ടെത്തൂ");
            local.put("home_sub", "ചില ലളിതമായ ചോദ്യങ്ങൾക്ക് മറുപടി നൽകുക. JanDwar ഓഫ്‌ലൈനിൽ പ്രവർത്തിക്കുകയും പരിശോധിച്ച വിവരങ്ങൾ മാത്രം കാണിക്കുകയും ചെയ്യും.");
            local.put("stat_roles", "NSQF ജോലികൾ");
            local.put("stat_fundable", "ധനസഹായ മേഖലകൾ");
            local.put("stat_districts", "തമിഴ്നാട് ജില്ലകൾ");
            local.put("stat_centres", "പരിശോധിച്ച കേന്ദ്രങ്ങൾ");
            local.put("start_intake", "എന്റെ ഓപ്ഷനുകൾ കണ്ടെത്തൂ");
            local.put("search", "തിരയുക");
            local.put("settings", "ക്രമീകരണം");
            local.put("settings_sub", "ആപ്പ് ലളിതവും സ്വകാര്യവും ഗ്രാമ ഉപയോഗത്തിന് തയ്യാറുമായിരിക്കുക.");
            local.put("offline_data", "ഓഫ്‌ലൈൻ ഡാറ്റ");
            local.put("offline_data_text", "ജോലികൾ, ജില്ലകൾ, കേന്ദ്ര വിവരങ്ങൾ ആപ്പിനുള്ളിൽ സൂക്ഷിച്ചിരിക്കുന്നു.");
            local.put("offline_ai", "ഓഫ്‌ലൈൻ AI ഡൗൺലോഡ് (45MB)");
            local.put("offline_ai_text", "ഇന്റർനെറ്റ് ഇല്ലാതെ വോയ്‌സ് ഉപയോഗിക്കാൻ ചെറിയ ലോക്കൽ മോഡൽ നേടുക.");
            local.put("privacy", "സ്വകാര്യത");
            local.put("privacy_text", "ഈ മാതൃകയിൽ വ്യക്തിഗത വിവരങ്ങൾ സംരക്ഷിക്കുകയോ അപ്‌ലോഡ് ചെയ്യുകയോ ഇല്ല.");
            local.put("app_name_label", "ആപ്പ്");
            local.put("honesty_note", "JanDwar കോഴ്‌സ്, കേന്ദ്രം, ഫീസ് അല്ലെങ്കിൽ സഹായധനം സൃഷ്ടിച്ച് കാണിക്കില്ല. വിവരം ഇല്ലെങ്കിൽ വ്യക്തമായി പറയും.");
            local.put("intake", "നിങ്ങളുടെ വിവരങ്ങൾ");
            local.put("mic_label", "മൈക്ക്");
            local.put("speak_button", "ഇപ്പോൾ സംസാരിക്കൂ");
            local.put("details", "വിശദാംശങ്ങൾ");
            local.put("level", "നില");
            local.put("short_term", "ഹ്രസ്വകാലം");
            local.put("long_term", "ദീർഘകാലം");
            local.put("asset_rule", "വായ്പയോടൊപ്പം ആസ്തി ചെലവിന്റെ 50% അല്ലെങ്കിൽ രൂപ 50,000 വരെ, ഏത് കുറവോ അത്.");
            local.put("not_claim_text", "ചേരുന്നതിന് മുമ്പ് അന്തിമ യോഗ്യത, ഫീസ്, ബാച്ച് തീയതി, അംഗീകാരം എന്നിവ TAHDCO അല്ലെങ്കിൽ കേന്ദ്രത്തിൽ സ്ഥിരീകരിക്കുക.");
            local.put("call", "കേന്ദ്രത്തെ വിളിക്കുക");
            local.put("no_results", "സുരക്ഷിതമായ പൊരുത്തം കണ്ടെത്തിയില്ല. പത്താം ക്ലാസ് അല്ലെങ്കിൽ മറ്റൊരു താൽപ്പര്യം ശ്രമിക്കുക.");
            local.put("reason_base", "വിദ്യാഭ്യാസം, താൽപ്പര്യം, പദ്ധതി നിയമങ്ങൾ, കേന്ദ്ര ലഭ്യത എന്നിവ ഉപയോഗിച്ച് പൊരുത്തപ്പെടുത്തി.");
            local.put("reason_interest", "നിങ്ങളുടെ താൽപ്പര്യവുമായി പൊരുത്തപ്പെടുന്നു:");
            local.put("on_0", "വലിയ ബട്ടണുകൾ അമർത്തുക അല്ലെങ്കിൽ ഒരു വാചകം പറയുക. ഫോം ബുദ്ധിമുട്ടില്ല.");
            local.put("on_1", "ശുപാർശകൾ PM-AJAY നിയമങ്ങളും വിദ്യാഭ്യാസ യോഗ്യതയും പാലിക്കുന്നു.");
            local.put("on_2", "നിങ്ങളുടെ ജില്ലയ്ക്ക് പരിശോധിച്ച കേന്ദ്രമുണ്ടെങ്കിൽ മാത്രമേ കാണിക്കൂ.");
        }
        return local.getOrDefault(key, "");
    }

    private void addFlowTranslations(Map<String, String> local) {
        if ("ta".equals(lang)) {
            local.put("voice_intro", "உங்கள் குரல் உதவியாளரை சந்திக்கவும்");
            local.put("voice_title", "JanDwar-ிடம் உங்கள் தேவையை சொல்லுங்கள்");
            local.put("voice_sub", "இயல்பாக பேசுங்கள். தொடங்க ஒரு நல்ல வழியை காண்போம்.");
            local.put("choose_path", "உங்கள் வழியை தேர்ந்தெடுக்கவும்");
            local.put("personalized_title", "உங்களுக்கான பயிற்சி தேடல்");
            local.put("personalized_sub", "மூன்று எளிய கேள்விகளுக்கு பதில் அளித்து விருப்பங்களை பெறுங்கள்.");
            local.put("browse_title", "அனைத்து பயிற்சிகளையும் பார்க்கவும்");
            local.put("browse_sub", "ஆஃப்லைன் பட்டியலில் உள்ள அனைத்து பயிற்சிகளையும் பாருங்கள்.");
            local.put("browse_action", "அனைத்து பயிற்சிகள்");
            local.put("browse_note", "இந்த செயலியில் 516 தகுதி பாடத்திட்டங்கள் உள்ளன. விவரங்களுக்கு ஒன்றைத் தட்டுங்கள்.");
            local.put("offline_search", "ஆஃப்லைன் தேடல்");
            local.put("offline_search_sub", "கீழே உள்ள தேர்வுகளைத் தட்டுங்கள். இணையம் அல்லது கணக்கு தேவையில்லை.");
            local.put("close", "மூடு");
            local.put("voice_listening", "கேட்கிறோம்");
            local.put("voice_hearing", "நான் உங்களைக் கேட்கிறேன்");
            local.put("voice_prompt", "இயல்பாக பேசுங்கள். முடிந்ததும் நிறுத்தவும்.");
            local.put("stop_listening", "கேட்பதை நிறுத்து");
            local.put("voice_unavailable", "இந்த தொலைபேசியில் குரல் வசதி இல்லை.");
        } else if ("hi".equals(lang)) {
            local.put("voice_intro", "अपने वॉइस सहायक से मिलें");
            local.put("voice_title", "JanDwar को अपनी जरूरत बताएं");
            local.put("voice_sub", "स्वाभाविक रूप से बोलें। हम शुरुआत के लिए सही रास्ता खोजेंगे।");
            local.put("choose_path", "अपना रास्ता चुनें");
            local.put("personalized_title", "आपके लिए कोर्स खोजें");
            local.put("personalized_sub", "तीन सरल सवालों के जवाब देकर विकल्प पाएं।");
            local.put("browse_title", "सभी कोर्स देखें");
            local.put("browse_sub", "ऑफलाइन सूची के हर कोर्स को देखें।");
            local.put("browse_action", "सभी कोर्स देखें");
            local.put("browse_note", "इस ऐप में 516 योग्यता पैक हैं। विवरण के लिए कोई कोर्स चुनें।");
            local.put("offline_search", "ऑफलाइन खोज");
            local.put("offline_search_sub", "नीचे विकल्प दबाएं। इंटरनेट या खाते की जरूरत नहीं है।");
            local.put("close", "बंद करें");
            local.put("voice_listening", "सुन रहे हैं");
            local.put("voice_hearing", "मैं आपकी बात सुन रहा हूं");
            local.put("voice_prompt", "स्वाभाविक रूप से बोलें। पूरा होने पर रोकें।");
            local.put("stop_listening", "सुनना रोकें");
            local.put("voice_unavailable", "इस फोन पर वॉइस सुविधा उपलब्ध नहीं है।");
        } else if ("te".equals(lang)) {
            local.put("voice_intro", "మీ వాయిస్ సహాయకుడిని కలవండి");
            local.put("voice_title", "JanDwar కు మీ అవసరాన్ని చెప్పండి");
            local.put("voice_sub", "సహజంగా మాట్లాడండి. ప్రారంభించడానికి సరైన మార్గం కనుగొంటాం.");
            local.put("choose_path", "మీ మార్గాన్ని ఎంచుకోండి");
            local.put("personalized_title", "మీ కోసం కోర్సు వెతకండి");
            local.put("personalized_sub", "మూడు సులభమైన ప్రశ్నలకు సమాధానం ఇచ్చి ఎంపికలు పొందండి.");
            local.put("browse_title", "అన్ని కోర్సులు చూడండి");
            local.put("browse_sub", "ఆఫ్లైన్ జాబితాలోని ప్రతి కోర్సును చూడండి.");
            local.put("browse_action", "అన్ని కోర్సులు");
            local.put("browse_note", "ఈ యాప్‌లో 516 అర్హత ప్యాక్‌లు ఉన్నాయి. వివరాల కోసం కోర్సును నొక్కండి.");
            local.put("offline_search", "ఆఫ్లైన్ శోధన");
            local.put("offline_search_sub", "క్రింద ఎంపికలను నొక్కండి. ఇంటర్నెట్ లేదా ఖాతా అవసరం లేదు.");
            local.put("close", "మూసివేయి");
            local.put("voice_listening", "వింటున్నాం");
            local.put("voice_hearing", "నేను మీ మాట వింటున్నాను");
            local.put("voice_prompt", "సహజంగా మాట్లాడండి. పూర్తయ్యాక ఆపండి.");
            local.put("stop_listening", "వినడం ఆపండి");
            local.put("voice_unavailable", "ఈ ఫోన్‌లో వాయిస్ సౌకర్యం లేదు.");
        } else if ("kn".equals(lang)) {
            local.put("voice_intro", "ನಿಮ್ಮ ಧ್ವನಿ ಸಹಾಯಕನನ್ನು ಭೇಟಿ ಮಾಡಿ");
            local.put("voice_title", "JanDwar ಗೆ ನಿಮ್ಮ ಅಗತ್ಯವನ್ನು ಹೇಳಿ");
            local.put("voice_sub", "ಸಹಜವಾಗಿ ಮಾತನಾಡಿ. ಪ್ರಾರಂಭಿಸಲು ಸರಿಯಾದ ದಾರಿ ಹುಡುಕುತ್ತೇವೆ.");
            local.put("choose_path", "ನಿಮ್ಮ ದಾರಿಯನ್ನು ಆರಿಸಿ");
            local.put("personalized_title", "ನಿಮಗಾಗಿ ಕೋರ್ಸ್ ಹುಡುಕಿ");
            local.put("personalized_sub", "ಮೂರು ಸರಳ ಪ್ರಶ್ನೆಗಳಿಗೆ ಉತ್ತರಿಸಿ ಆಯ್ಕೆಗಳನ್ನು ಪಡೆಯಿರಿ.");
            local.put("browse_title", "ಎಲ್ಲಾ ಕೋರ್ಸ್‌ಗಳನ್ನು ನೋಡಿ");
            local.put("browse_sub", "ಆಫ್‌ಲೈನ್ ಪಟ್ಟಿಯಲ್ಲಿರುವ ಎಲ್ಲಾ ಕೋರ್ಸ್‌ಗಳನ್ನು ನೋಡಿ.");
            local.put("browse_action", "ಎಲ್ಲಾ ಕೋರ್ಸ್‌ಗಳು");
            local.put("browse_note", "ಈ ಆಪ್‌ನಲ್ಲಿ 516 ಅರ್ಹತಾ ಪ್ಯಾಕ್‌ಗಳಿವೆ. ವಿವರಗಳಿಗಾಗಿ ಕೋರ್ಸ್ ಒತ್ತಿರಿ.");
            local.put("offline_search", "ಆಫ್‌ಲೈನ್ ಹುಡುಕಾಟ");
            local.put("offline_search_sub", "ಕೆಳಗಿನ ಆಯ್ಕೆಗಳನ್ನು ಒತ್ತಿರಿ. ಇಂಟರ್ನೆಟ್ ಅಥವಾ ಖಾತೆ ಅಗತ್ಯವಿಲ್ಲ.");
            local.put("close", "ಮುಚ್ಚಿ");
            local.put("voice_listening", "ಕೇಳುತ್ತಿದ್ದೇವೆ");
            local.put("voice_hearing", "ನಾನು ನಿಮ್ಮ ಮಾತು ಕೇಳುತ್ತಿದ್ದೇನೆ");
            local.put("voice_prompt", "ಸಹಜವಾಗಿ ಮಾತನಾಡಿ. ಮುಗಿದ ನಂತರ ನಿಲ್ಲಿಸಿ.");
            local.put("stop_listening", "ಕೇಳುವುದನ್ನು ನಿಲ್ಲಿಸಿ");
            local.put("voice_unavailable", "ಈ ಫೋನ್‌ನಲ್ಲಿ ಧ್ವನಿ ಸೌಲಭ್ಯವಿಲ್ಲ.");
        } else if ("ml".equals(lang)) {
            local.put("voice_intro", "നിങ്ങളുടെ വോയ്സ് സഹായിയെ പരിചയപ്പെടൂ");
            local.put("voice_title", "JanDwar-നോട് നിങ്ങളുടെ ആവശ്യം പറയൂ");
            local.put("voice_sub", "സ്വാഭാവികമായി സംസാരിക്കൂ. തുടങ്ങാനുള്ള ശരിയായ വഴി കണ്ടെത്താം.");
            local.put("choose_path", "നിങ്ങളുടെ വഴി തിരഞ്ഞെടുക്കൂ");
            local.put("personalized_title", "നിങ്ങൾക്കായുള്ള കോഴ്സ് കണ്ടെത്തൽ");
            local.put("personalized_sub", "മൂന്ന് ലളിതമായ ചോദ്യങ്ങൾക്ക് ഉത്തരം നൽകി ഓപ്ഷനുകൾ നേടൂ.");
            local.put("browse_title", "എല്ലാ കോഴ്സുകളും കാണൂ");
            local.put("browse_sub", "ഓഫ്‌ലൈൻ പട്ടികയിലെ എല്ലാ കോഴ്സുകളും പരിശോധിക്കൂ.");
            local.put("browse_action", "എല്ലാ കോഴ്സുകളും");
            local.put("browse_note", "ഈ ആപ്പിൽ 516 യോഗ്യതാ പാക്കുകളുണ്ട്. വിശദാംശങ്ങൾക്ക് ഒരു കോഴ്സ് തിരഞ്ഞെടുക്കൂ.");
            local.put("offline_search", "ഓഫ്‌ലൈൻ തിരയൽ");
            local.put("offline_search_sub", "താഴെയുള്ള ഓപ്ഷനുകൾ അമർത്തൂ. ഇന്റർനെറ്റോ അക്കൗണ്ടോ ആവശ്യമില്ല.");
            local.put("close", "അടയ്ക്കുക");
            local.put("voice_listening", "കേൾക്കുന്നു");
            local.put("voice_hearing", "ഞാൻ നിങ്ങളെ കേൾക്കുന്നു");
            local.put("voice_prompt", "സ്വാഭാവികമായി സംസാരിക്കൂ. തീർന്നാൽ നിർത്തൂ.");
            local.put("stop_listening", "കേൾക്കുന്നത് നിർത്തുക");
            local.put("voice_unavailable", "ഈ ഫോണിൽ വോയ്സ് സൗകര്യം ലഭ്യമല്ല.");
        }
    }

    private String languageMeaning(String code) {
        if ("ta".equals(code)) return "மக்களுக்கான நுழைவாயில்";
        if ("hi".equals(code)) return "नागरिकों का प्रवेश द्वार";
        if ("te".equals(code)) return "పౌరుల కోసం ద్వారం";
        if ("kn".equals(code)) return "ನಾಗರಿಕರ ದ್ವಾರ";
        if ("ml".equals(code)) return "പൗരന്മാർക്കുള്ള കവാടം";
        return "Gateway for Citizens";
    }

    private String languageEnglishName(String code) {
        if ("ta".equals(code)) return "Tamil";
        if ("hi".equals(code)) return "Hindi";
        if ("te".equals(code)) return "Telugu";
        if ("kn".equals(code)) return "Kannada";
        if ("ml".equals(code)) return "Malayalam";
        return "English";
    }

    private String onTitle(int page) {
        if (page == 0) return tr("voice_intro");
        if (page == 1) return tr("fundable_badge");
        return tr("centre");
    }

    private String onBody(int page) {
        if (page == 0) return tr("on_0").equals("on_0") ? "Use big taps or one spoken sentence. No form stress." : tr("on_0");
        if (page == 1) return tr("on_1").equals("on_1") ? "Recommendations follow PM-AJAY rules and education gates." : tr("on_1");
        return tr("on_2").equals("on_2") ? "Centres are shown only when verified for your district." : tr("on_2");
    }

    private String speechLocale() {
        if ("ta".equals(lang)) return "ta-IN";
        if ("hi".equals(lang)) return "hi-IN";
        if ("te".equals(lang)) return "te-IN";
        if ("kn".equals(lang)) return "kn-IN";
        if ("ml".equals(lang)) return "ml-IN";
        return "en-IN";
    }

    private int eduRank(String edu) {
        if ("edu_below8".equals(edu)) return 0;
        if ("edu_8".equals(edu)) return 2;
        if ("edu_10".equals(edu)) return 3;
        if ("edu_12".equals(edu)) return 4;
        return 6;
    }

    private String interestList() {
        StringBuilder b = new StringBuilder();
        for (String key : selectedInterests) {
            if (b.length() > 0) b.append(", ");
            b.append(interest(key));
        }
        return b.toString();
    }

    private String sectorName(String sector) {
        if ("food_processing".equals(sector)) return interest("food");
        if ("construction".equals(sector)) return interest("construction");
        if ("handloom_textile".equals(sector) || "apparel".equals(sector)) return interest("textile");
        if ("agriculture".equals(sector)) return interest("farming");
        return sector.replace("_", " ");
    }

    private interface SelectListener { void set(String key); }

    private class Rec {
        final Role role;
        final int score;
        final String reason;
        Rec(Role role, int score, String reason) {
            this.role = role;
            this.score = score;
            this.reason = reason;
        }
    }

    private class Role {
        final String code;
        final String name;
        final String levelRaw;
        final String hoursRaw;
        final String ssc;
        final String sector;

        Role(JSONObject obj) {
            code = obj.optString("qp_code");
            name = obj.optString("job_role");
            levelRaw = obj.optString("nsqf_level");
            hoursRaw = obj.optString("notional_hours");
            ssc = obj.optString("ssc");
            sector = obj.optString("sector");
        }

        boolean validName() {
            String lower = name.toLowerCase(Locale.ROOT);
            return name.length() > 3 && !lower.equals("english hindi") && !name.startsWith("QG-");
        }

        int level() {
            try { return Integer.parseInt(levelRaw.replaceAll("[^0-9]", "")); }
            catch (Exception e) { return 3; }
        }

        String levelLabel() {
            return levelRaw.length() == 0 ? tr("level") + " " + level() + " (inferred)" : levelRaw;
        }

        int hours() {
            try { return Integer.parseInt(hoursRaw.replaceAll("[^0-9]", "")); }
            catch (Exception e) { return 300; }
        }

        boolean longTerm() {
            return hours() >= 600;
        }

        int requiredEdu() {
            int l = level();
            if (l <= 2) return 0;
            if (l == 3) return 2;
            if (l == 4) return 3;
            if (l == 5) return 4;
            return 6;
        }

        boolean fundable() {
            return "agriculture".equals(sector) || "food_processing".equals(sector)
                    || "construction".equals(sector) || "handloom_textile".equals(sector);
        }

        boolean matchesInterest(String key) {
            String n = name.toLowerCase(Locale.ROOT);
            if ("dairy".equals(key)) return sector.equals("agriculture") || n.contains("dairy") || n.contains("milk");
            if ("cattle".equals(key)) return n.contains("cattle") || n.contains("livestock") || sector.equals("agriculture");
            if ("goat".equals(key)) return n.contains("goat") || n.contains("sheep") || sector.equals("agriculture");
            if ("poultry".equals(key)) return n.contains("poultry") || n.contains("chicken") || sector.equals("agriculture");
            if ("farming".equals(key)) return sector.equals("agriculture") || n.contains("farm");
            if ("food".equals(key)) return sector.equals("food_processing") || n.contains("food");
            if ("machine".equals(key)) return n.contains("machine") || n.contains("technician") || n.contains("operator");
            if ("textile".equals(key)) return sector.equals("handloom_textile") || sector.equals("apparel");
            if ("construction".equals(key)) return sector.equals("construction");
            if ("tailor".equals(key)) return sector.equals("apparel") || n.contains("sewing") || n.contains("tailor");
            return false;
        }

        boolean selfEmploymentFit() {
            String n = name.toLowerCase(Locale.ROOT);
            return sector.equals("agriculture") || n.contains("entrepreneur") || n.contains("farm") || n.contains("artisan");
        }

        boolean wageFit() {
            String n = name.toLowerCase(Locale.ROOT);
            return n.contains("assistant") || n.contains("operator") || n.contains("technician")
                    || n.contains("worker") || n.contains("supervisor");
        }
    }

    private void addRipple(View view) {
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            android.graphics.drawable.Drawable bg = view.getBackground();
            android.content.res.ColorStateList color = android.content.res.ColorStateList.valueOf(Color.argb(40, 0, 0, 0));
            android.graphics.drawable.RippleDrawable ripple = new android.graphics.drawable.RippleDrawable(color, bg, null);
            view.setBackground(ripple);
        }
    }
}
