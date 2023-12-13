//package com.egeniq.androidtvprogramguide.util;
package androidx.leanback.widget;

public class ProgramGuideLeanbackExtensions {
    public static void setFocusOutAllowed(BaseGridView baseGridView, boolean throughFront, boolean throughEnd) {
        baseGridView.mLayoutManager.setFocusOutAllowed(throughFront, throughEnd);
    }

    public static void setFocusOutSideAllowed(BaseGridView baseGridView, boolean throughStart, boolean throughEnd) {
        baseGridView.mLayoutManager.setFocusOutSideAllowed(throughStart, throughEnd);
    }
}
