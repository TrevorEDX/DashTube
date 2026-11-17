/*
 * DashTube - YouTube on the Android Auto screen.
 *
 * Based on the approach and web-view setup of AABrowser
 * (https://github.com/kododake/AABrowser), Copyright (C) 2025 AABrowser Contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.trevoredx.dashtube;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.webkit.UserAgentMetadata;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import java.util.Arrays;
import java.util.Locale;

public class MainActivity extends Activity {

    static final String HOME_URL = "https://m.youtube.com/";

    // Same browser identity AABrowser uses for its default "Android Chrome" profile.
    // Presenting as regular mobile Chrome (no WebView "wv" marker) is what lets
    // Google sign-in and the normal YouTube mobile site work inside the app.
    private static final String CHROME_VERSION = "154.0.0.0";
    private static final String MOBILE_CHROME_UA =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/"
                    + CHROME_VERSION + " Mobile Safari/537.36";

    /** Sites the app may navigate to: YouTube, plus Google's sign-in and consent pages. */
    private static final String[] ALLOWED_DOMAINS = {
            "youtube.com", "youtu.be", "youtube-nocookie.com", "ytimg.com",
            "googlevideo.com", "gstatic.com", "googleusercontent.com", "ggpht.com",
            "googleapis.com", "recaptcha.net", "withgoogle.com"
    };

    private LinearLayout mainLayout;
    private LinearLayout rail;
    private FrameLayout fullscreenContainer;
    private WebView webView;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private OnBackInvokedCallback backCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setFitsSystemWindows(true);

        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.HORIZONTAL);

        rail = buildRail();
        mainLayout.addView(rail, new LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.MATCH_PARENT));

        webView = new WebView(this);
        configureWebView(webView);
        mainLayout.addView(webView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        root.addView(mainLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        fullscreenContainer = new FrameLayout(this);
        fullscreenContainer.setBackgroundColor(Color.BLACK);
        fullscreenContainer.setVisibility(View.GONE);
        root.addView(fullscreenContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        registerBackHandling();

        String startUrl = allowedUrlFromIntent(getIntent());
        if (startUrl != null) {
            webView.loadUrl(startUrl);
        } else if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(HOME_URL);
        }
    }

    // ---------------------------------------------------------------- web view

    private void configureWebView(WebView view) {
        view.setBackgroundColor(Color.BLACK);
        view.setHorizontalScrollBarEnabled(false);

        WebSettings s = view.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false); // pop-ups open in the same view
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSafeBrowsingEnabled(true);
        s.setOffscreenPreRaster(true);
        s.setUserAgentString(MOBILE_CHROME_UA);
        s.setUseWideViewPort(false);
        s.setLoadWithOverviewMode(false);
        s.setTextZoom(100);
        view.setInitialScale((int) (getResources().getDisplayMetrics().density * 100));
        applyChromeUserAgentMetadata(s);

        // Keep the Google/YouTube login between launches.
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(view, true);

        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                return blockNavigation(v, request.getUrl(), request.isForMainFrame());
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                super.onPageFinished(v, url);
                CookieManager.getInstance().flush();
            }

            @Override
            public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail detail) {
                // The page crashed: throw this view away and start fresh.
                ViewGroup parent = (ViewGroup) v.getParent();
                if (parent != null) parent.removeView(v);
                v.destroy();
                if (v == webView) webView = null;
                recreate();
                return true;
            }
        });

        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View fullscreenView, CustomViewCallback callback) {
                enterFullscreen(fullscreenView, callback);
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen(false);
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                // Only protected-media (DRM) playback, e.g. YouTube movies.
                if (Arrays.asList(request.getResources()).contains(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID});
                } else {
                    request.deny();
                }
            }
        });
    }

    private static void applyChromeUserAgentMetadata(WebSettings settings) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) return;
        UserAgentMetadata metadata = new UserAgentMetadata.Builder()
                .setBrandVersionList(Arrays.asList(
                        new UserAgentMetadata.BrandVersion.Builder()
                                .setBrand("Google Chrome")
                                .setMajorVersion(CHROME_VERSION.substring(0, CHROME_VERSION.indexOf('.')))
                                .setFullVersion(CHROME_VERSION)
                                .build(),
                        new UserAgentMetadata.BrandVersion.Builder()
                                .setBrand("Chromium")
                                .setMajorVersion(CHROME_VERSION.substring(0, CHROME_VERSION.indexOf('.')))
                                .setFullVersion(CHROME_VERSION)
                                .build()))
                .setFullVersion(CHROME_VERSION)
                .setPlatform("Android")
                .setPlatformVersion("10.0.0")
                .setArchitecture("")
                .setModel("")
                .setMobile(true)
                .setBitness(UserAgentMetadata.BITNESS_DEFAULT)
                .setWow64(false)
                .build();
        WebSettingsCompat.setUserAgentMetadata(settings, metadata);
    }

    /** Returns true to block the navigation (anything that isn't YouTube or Google sign-in). */
    private boolean blockNavigation(WebView v, Uri uri, boolean mainFrame) {
        if (uri == null) return true;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);

        if (scheme.equals("intent")) {
            // "Open in the YouTube app" links: stay here, use the web fallback if it's YouTube.
            try {
                Intent intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                String fallback = intent.getStringExtra("browser_fallback_url");
                if (fallback != null && isAllowedHost(Uri.parse(fallback).getHost())) {
                    v.loadUrl(fallback);
                }
            } catch (Exception ignored) {
            }
            return true;
        }
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return !scheme.equals("about") && !scheme.equals("data") && !scheme.equals("javascript");
        }
        if (!mainFrame || isAllowedHost(uri.getHost())) return false;

        Toast.makeText(this, R.string.youtube_only, Toast.LENGTH_SHORT).show();
        return true;
    }

    static boolean isAllowedHost(String host) {
        if (host == null || host.isEmpty()) return false;
        host = host.toLowerCase(Locale.ROOT);
        for (String domain : ALLOWED_DOMAINS) {
            if (host.equals(domain) || host.endsWith("." + domain)) return true;
        }
        // google.com, accounts.google.com, google.ca, accounts.google.co.uk ...
        String[] labels = host.split("\\.");
        for (int i = 0; i < labels.length - 1; i++) {
            if (labels[i].equals("google")) {
                int tldLabels = labels.length - i - 1;
                if (tldLabels == 1) return true;                       // google.com, google.ca
                return tldLabels == 2                                  // google.co.uk, google.com.au
                        && (labels[i + 1].equals("co") || labels[i + 1].equals("com"))
                        && labels[i + 2].length() == 2;
            }
        }
        return false;
    }

    private String allowedUrlFromIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return null;
        Uri data = intent.getData();
        if (data == null || !"https".equalsIgnoreCase(data.getScheme())) return null;
        return isAllowedHost(data.getHost()) ? data.toString() : null;
    }

    // ---------------------------------------------------------------- fullscreen video

    private void enterFullscreen(View view, WebChromeClient.CustomViewCallback callback) {
        if (customView != null) {
            callback.onCustomViewHidden();
            return;
        }
        customView = view;
        customViewCallback = callback;
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fullscreenContainer.setVisibility(View.VISIBLE);
        fullscreenContainer.bringToFront();
        mainLayout.setVisibility(View.INVISIBLE);
    }

    private void exitFullscreen(boolean notifyPage) {
        if (customView == null) return;
        fullscreenContainer.removeAllViews();
        fullscreenContainer.setVisibility(View.GONE);
        mainLayout.setVisibility(View.VISIBLE);
        WebChromeClient.CustomViewCallback callback = customViewCallback;
        customView = null;
        customViewCallback = null;
        if (notifyPage && callback != null) callback.onCustomViewHidden();
    }

    // ---------------------------------------------------------------- buttons and back

    private LinearLayout buildRail() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(getColor(R.color.dashtube_rail));
        bar.addView(railButton(R.drawable.ic_back, R.string.back, v -> goBack()));
        bar.addView(railButton(R.drawable.ic_home, R.string.home, v -> {
            if (webView != null) webView.loadUrl(HOME_URL);
        }));
        bar.addView(railButton(R.drawable.ic_reload, R.string.reload, v -> {
            if (webView != null) webView.reload();
        }));
        return bar;
    }

    private ImageView railButton(int iconRes, int descriptionRes, View.OnClickListener onClick) {
        ImageView button = new ImageView(this);
        button.setImageResource(iconRes);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setContentDescription(getString(descriptionRes));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x33FFFFFF);
        button.setBackground(bg);
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
        lp.setMargins(0, dp(10), 0, dp(10));
        button.setLayoutParams(lp);
        return button;
    }

    private void goBack() {
        if (customView != null) {
            exitFullscreen(true);
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
        }
    }

    private void registerBackHandling() {
        backCallback = () -> {
            if (customView != null || (webView != null && webView.canGoBack())) {
                goBack();
            } else {
                finish();
            }
        };
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String url = allowedUrlFromIntent(intent);
        if (url != null && webView != null) webView.loadUrl(url);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        exitFullscreen(true);
        if (webView != null) webView.onPause();
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) webView.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        if (backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        }
        if (webView != null) {
            webView.stopLoading();
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) parent.removeView(webView);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
