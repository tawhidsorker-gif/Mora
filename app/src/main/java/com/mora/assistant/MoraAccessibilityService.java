package com.mora.assistant;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class MoraAccessibilityService extends AccessibilityService {

    private static MoraAccessibilityService instance;

    public static MoraAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        instance = this;
    }

    @Override
    public void onInterrupt() {
        instance = null;
    }

    public boolean clickTextOrDesc(String target) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || target == null) return false;
        return findAndClick(root, target.trim().toLowerCase());
    }

    private boolean findAndClick(AccessibilityNodeInfo node, String target) {
        if (node == null) return false;
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        boolean match = (text != null && text.toString().toLowerCase().contains(target))
                || (desc != null && desc.toString().toLowerCase().contains(target));

        if (match) {
            AccessibilityNodeInfo current = node;
            while (current != null) {
                if (current.isClickable()) {
                    current.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return true;
                }
                current = current.getParent();
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            if (findAndClick(node.getChild(i), target)) {
                return true;
            }
        }
        return false;
    }

    public boolean typeInFocusedOrFirstEditText(String message) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || message == null) return false;
        AccessibilityNodeInfo target = findEditText(root);
        if (target == null) return false;

        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message);
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private AccessibilityNodeInfo findEditText(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findEditText(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    public boolean clickSendButton() {
        return clickTextOrDesc("send") || clickTextOrDesc("পাঠান");
    }
}
