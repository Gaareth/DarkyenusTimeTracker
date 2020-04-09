package com.darkyen;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static com.darkyen.TimeTrackingStatus.RUNNING;

/**
 * The custom widget that is the main UI of the plugin.
 *
 * NOTES:
 * - Not implementing getPresentation(), because it is not used for CustomStatusBarWidgets.
 * - AWTEventListener is for inactivity listening
 */
public final class TimeTrackerWidget extends JButton implements CustomStatusBarWidget {

    // Synchronized with xml
    public static final String ID = "com.darkyen.DarkyenusTimeTracker";

    @NotNull
    private final TimeTrackerService service;

    private boolean mouseInside = false;

    TimeTrackerWidget(@NotNull TimeTrackerService service) {
        this.service = service;
        addActionListener(e -> {
            final AWTEvent event = EventQueue.getCurrentEvent();
            if (event instanceof MouseEvent) {
                final MouseEvent mouseEvent = (MouseEvent) event;
                final int actionSplit = getWidth() / 2;
                if (mouseEvent.getX() < actionSplit) {
                    service.toggleRunning();
                } else {
                    popupSettings();
                }
            }
        });
        setBorder(StatusBarWidget.WidgetBorder.INSTANCE);
        setOpaque(false);
        setFocusable(false);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                mouseInside = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                mouseInside = false;
                repaint();
            }
        });
    }

    private void popupSettings() {
        final TimeTrackerPopupContent content = new TimeTrackerPopupContent(service);

        final ComponentPopupBuilder popupBuilder = JBPopupFactory.getInstance().createComponentPopupBuilder(content, null);
        popupBuilder.setCancelOnClickOutside(true);
        popupBuilder.setFocusable(true);
        popupBuilder.setRequestFocus(true);
        popupBuilder.setShowBorder(true);
        popupBuilder.setShowShadow(true);
        final JBPopup popup = popupBuilder.createPopup();
        content.popup = popup;

        final Rectangle visibleRect = TimeTrackerWidget.this.getVisibleRect();
        final Dimension preferredSize = content.getPreferredSize();
        final RelativePoint point = new RelativePoint(TimeTrackerWidget.this, new Point(visibleRect.x+visibleRect.width - preferredSize.width, visibleRect.y - (preferredSize.height + 15)));
        popup.show(point);

        // Not sure if needed, but sometimes the popup is not clickable for some mysterious reason
        // and it stopped happening when this was added
        content.requestFocus();
    }

    @NotNull
    @Override
    public String ID() {
        return ID;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {}

    @Override
    public void dispose() {}

    private int lastTimeToShow = -1;

    @Override
    public void paintComponent(final Graphics g) {
        final int timeToShow = service.getTotalTimeSeconds();
        final String info = service.getIdeTimePattern().secondsToString(timeToShow);

        if (timeToShow != lastTimeToShow) {
            lastTimeToShow = timeToShow;
            setToolTipText(FULL_TIME_FORMATTING.secondsToString(timeToShow));
        }

        final Dimension size = getSize();
        final Insets insets = getInsets();

        final int totalBarLength = size.width - insets.left - insets.right;
        final int barHeight = Math.max(size.height, getFont().getSize() + 2);
        final int yOffset = (size.height - barHeight) / 2;
        final int xOffset = insets.left;

        final TimeTrackingStatus status = service.getStatus();
        if (mouseInside) {
            if (status == RUNNING) {
                g.setColor(COLOR_MENU_ON);
            } else {
                g.setColor(COLOR_MENU_OFF);
            }
        } else {
            switch (status) {
                case RUNNING:
                    g.setColor(COLOR_ON);
                    break;
                case IDLE:
                    g.setColor(COLOR_IDLE);
                    break;
                case STOPPED:
                    g.setColor(COLOR_OFF);
                    break;
            }
        }
        g.fillRect(insets.left, insets.bottom, totalBarLength, size.height - insets.bottom - insets.top);
        UISettings.setupAntialiasing(g);

        if (mouseInside) {
            // Draw controls
            g.setColor(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground());
            int settingsLength = Math.max(totalBarLength / 5, SETTINGS_ICON.getIconWidth() / 2 * 3);
            int runResumeLength = totalBarLength - settingsLength;

            g.drawLine(xOffset + runResumeLength, yOffset, xOffset + runResumeLength, yOffset + barHeight);

            Icon firstIcon = status == RUNNING ? STOP_ICON : START_ICON;
            firstIcon.paintIcon(this, g, xOffset + (runResumeLength - firstIcon.getIconWidth()) / 2, yOffset + (barHeight - firstIcon.getIconHeight())/2);
            SETTINGS_ICON.paintIcon(this, g, xOffset + runResumeLength + (totalBarLength - runResumeLength - SETTINGS_ICON.getIconWidth()) / 2, yOffset + (barHeight - SETTINGS_ICON.getIconHeight())/2);
        } else {
            // Draw time text
            final Color fg = getModel().isPressed() ? UIUtil.getLabelDisabledForeground() : JBColor.foreground();
            g.setColor(fg);
            g.setFont(WIDGET_FONT);
            final FontMetrics fontMetrics = g.getFontMetrics();
            final int infoWidth = fontMetrics.charsWidth(info.toCharArray(), 0, info.length());
            final int infoHeight = fontMetrics.getAscent();
            g.drawString(info, xOffset + (totalBarLength - infoWidth) / 2, yOffset + infoHeight + (barHeight - infoHeight) / 2 - 1);
        }
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    private TimePattern getPreferredSize_lastPattern = null;
    private Font getPreferredSize_lastFont = null;
    private int getPreferredSize_lastWidth = -1;

    @Override
    public Dimension getPreferredSize() {
        final Font widgetFont = WIDGET_FONT;
        final FontMetrics fontMetrics = getFontMetrics(widgetFont);
        final TimePattern pattern = service.getIdeTimePattern();
        final int stringWidth;

        if (widgetFont.equals(getPreferredSize_lastFont) && pattern.equals(getPreferredSize_lastPattern)) {
            stringWidth = getPreferredSize_lastWidth;
        } else {
            int maxWidth = 0;
            // Size may decrease with growing time, so we try different second boundaries
            for (int seconds : PREFERRED_SIZE_SECOND_QUERIES) {
                maxWidth = Math.max(maxWidth, fontMetrics.stringWidth(pattern.secondsToString(seconds - 1)));
            }
            getPreferredSize_lastPattern = pattern;
            getPreferredSize_lastFont = widgetFont;
            getPreferredSize_lastWidth = maxWidth;
            stringWidth = maxWidth;
        }


        final Insets insets = getInsets();
        int width = stringWidth + insets.left + insets.right + JBUI.scale(2);
        int height = fontMetrics.getHeight() + insets.top + insets.bottom + JBUI.scale(2);
        return new Dimension(width, height);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    private static final Icon SETTINGS_ICON = AllIcons.General.Settings;
    private static final Icon START_ICON = AllIcons.Actions.Resume;
    private static final Icon STOP_ICON = AllIcons.Actions.Pause;
    private static final Font WIDGET_FONT = JBUI.Fonts.label(11);

    private static final Color COLOR_OFF = new JBColor(new Color(189, 0, 16), new Color(128, 0, 0));
    private static final Color COLOR_ON = new JBColor(new Color(28, 152, 19), new Color(56, 113, 41));
    private static final Color COLOR_IDLE = new JBColor(new Color(200, 164, 23), new Color(163, 112, 17));

    private static final Color COLOR_MENU_OFF = new JBColor(new Color(198, 88, 97), new Color(97, 38, 38));
    private static final Color COLOR_MENU_ON = new JBColor(new Color(133, 194, 130), new Color(55, 80, 48));

    public static final TimePattern FULL_TIME_FORMATTING = TimePattern.parse("{{lw \"week\"s}} {{ld \"day\"s}} {{lh \"hour\"s}} {{lm \"minute\"s}} {{s \"second\"s}}");

    private static final int[] PREFERRED_SIZE_SECOND_QUERIES = {
            60,
            60 * 60,
            60 * 60 * 24,
            60 * 60 * 24 * 7,
            1999999999
    };
}
