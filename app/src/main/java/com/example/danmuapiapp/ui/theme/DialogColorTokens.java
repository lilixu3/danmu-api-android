package com.example.danmuapiapp.ui.theme;

/**
 * Semantic color tokens shared by Compose dialogs and dialogs injected into host apps.
 *
 * <p>Surfaces use the app's low-chroma blue-gray foundation while brand color is reserved for
 * actions, selection and focus. Keeping raw ARGB values here avoids the Compose and Xposed
 * implementations drifting into separate palettes.</p>
 */
public final class DialogColorTokens {

    private DialogColorTokens() {
    }

    // Light surfaces and text.
    public static final int LIGHT_DIALOG = 0xFFFAFBFD;
    public static final int LIGHT_SURFACE_CONTAINER = 0xFFF7F8FA;
    public static final int LIGHT_SURFACE_HIGH = 0xFFF2F4F7;
    public static final int LIGHT_SURFACE_ACTIVE = 0xFFE7EBF0;
    public static final int LIGHT_TEXT_PRIMARY = 0xFF1D232C;
    public static final int LIGHT_TEXT_SECONDARY = 0xFF626C79;
    public static final int LIGHT_TEXT_MUTED = 0xFF7B8592;
    public static final int LIGHT_OUTLINE = 0xFF8793A2;
    public static final int LIGHT_OUTLINE_VARIANT = 0xFFD3DAE4;

    // Light action and selection.
    public static final int LIGHT_PRIMARY = 0xFF476F9E;
    public static final int LIGHT_PRIMARY_PRESSED = 0xFF365F8E;
    public static final int LIGHT_ON_PRIMARY = 0xFFFFFFFF;
    public static final int LIGHT_PRIMARY_CONTAINER = 0xFFE3EEF7;
    public static final int LIGHT_ON_PRIMARY_CONTAINER = 0xFF335C83;
    public static final int LIGHT_FOCUS_RING = 0xFF244E78;
    public static final int LIGHT_FOCUS_FILL = 0x1F476F9E;

    // Light semantic tones.
    public static final int LIGHT_SUCCESS = 0xFF2F704C;
    public static final int LIGHT_SUCCESS_CONTAINER = 0xFFE3F3E9;
    public static final int LIGHT_WARNING = 0xFF7A5315;
    public static final int LIGHT_WARNING_CONTAINER = 0xFFF8EED8;
    public static final int LIGHT_ERROR = 0xFFB74450;
    public static final int LIGHT_ON_ERROR = 0xFFFFFFFF;
    public static final int LIGHT_ERROR_CONTAINER = 0xFFFBE6E8;
    public static final int LIGHT_ON_ERROR_CONTAINER = 0xFF7F2831;

    // Dark surfaces and text.
    public static final int DARK_DIALOG = 0xFF1F2D47;
    public static final int DARK_SURFACE_LOWEST = 0xFF111A2E;
    public static final int DARK_SURFACE_CONTAINER = 0xFF17243B;
    public static final int DARK_SURFACE_HIGH = 0xFF253653;
    public static final int DARK_SURFACE_ACTIVE = 0xFF2C4162;
    public static final int DARK_TEXT_PRIMARY = 0xFFE6EAFA;
    public static final int DARK_TEXT_SECONDARY = 0xFFB1BDD4;
    public static final int DARK_TEXT_MUTED = 0xFF8D9AB2;
    public static final int DARK_OUTLINE = 0xFF7D8DA9;
    public static final int DARK_OUTLINE_VARIANT = 0xFF435574;

    // Dark action and selection.
    public static final int DARK_PRIMARY = 0xFF7DCFFF;
    public static final int DARK_PRIMARY_PRESSED = 0xFF65B8E6;
    public static final int DARK_ON_PRIMARY = 0xFF031A28;
    public static final int DARK_PRIMARY_CONTAINER = 0xFF294765;
    public static final int DARK_ON_PRIMARY_CONTAINER = 0xFFC8EBFF;
    public static final int DARK_FOCUS_RING = 0xFFE1F3FF;
    public static final int DARK_FOCUS_FILL = 0x267DCFFF;

    // Dark semantic tones.
    public static final int DARK_SUCCESS = 0xFFACE7C1;
    public static final int DARK_SUCCESS_CONTAINER = 0xFF1C3B30;
    public static final int DARK_WARNING = 0xFFF2D091;
    public static final int DARK_WARNING_CONTAINER = 0xFF44351F;
    public static final int DARK_ERROR = 0xFFF2A0A6;
    public static final int DARK_ON_ERROR = 0xFF2A090C;
    public static final int DARK_ERROR_CONTAINER = 0xFF472B33;
    public static final int DARK_ON_ERROR_CONTAINER = 0xFFFFD0D4;
}
