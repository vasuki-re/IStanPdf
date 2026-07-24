package vasuki.istanpdf.presentation;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

import vasuki.istanpdf.R;
import vasuki.istanpdf.ThemePrefs;
import vasuki.istanpdf.model.PageItem;

public class EditorViewBuilder {

    public interface EditorActions {
        void onBack();
        void onSave(String titleText, boolean docxExport);
        void onAddItems(String titleText);
        void onShowCustomDialog(String title, View content, String negativeStr, Runnable negativeAction, String positiveStr, Runnable positiveAction);
        void toast(String message);
        List<PageItem> getPages();
        List<Uri> getPendingUris();
        boolean isPagesAdded();
    }

    private static final String WAITING_TEXT = "Ready";

    private final Activity activity;
    private final Typeface regularFont;
    private final Typeface boldFont;

    private TextView status;
    private ImageView statusIndicator;
    private RecyclerView pageList;

    public EditorViewBuilder(Activity activity, Typeface regularFont, Typeface boldFont) {
        this.activity = activity;
        this.regularFont = regularFont;
        this.boldFont = boldFont;
    }

    public View build(String titleText, String saveLabelText, boolean docxExport, boolean allowReorder, EditorActions actions) {
        LinearLayout outer = new LinearLayout(activity);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(color(R.color.istan_background));

        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(dp(12), dp(16), dp(22), dp(16));
        outer.addView(titleRow);

        TextView backArrow = text("\u2190", 28, R.color.istan_text, true);
        backArrow.setPadding(0, 0, dp(16), dp(4));
        backArrow.setOnClickListener(v -> actions.onBack());
        titleRow.addView(backArrow);

        TextView title = text(titleText, 22, R.color.istan_text, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        View separator = new View(activity);
        separator.setBackgroundColor(ThemePrefs.isAmoled(activity) ? 0xFF333333 : 0xFFB4B8AA);
        outer.addView(separator, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(22), dp(16), dp(22), 0);
        outer.addView(header);

        LinearLayout statusCard = new LinearLayout(activity);
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setGravity(Gravity.CENTER);
        statusCard.setBaselineAligned(false);
        statusCard.setPadding(dp(16), dp(10), dp(20), dp(10));
        android.graphics.drawable.GradientDrawable statusBg = new android.graphics.drawable.GradientDrawable();
        statusBg.setColor(color(R.color.istan_surface));
        statusBg.setCornerRadius(dp(28));
        statusBg.setStroke(dp(1), color(R.color.istan_outline));
        statusCard.setBackground(statusBg);

        statusIndicator = new ImageView(activity);
        android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
        dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dot.setColor(color(R.color.istan_olive));
        dot.setSize(dp(12), dp(12));
        statusIndicator.setImageDrawable(dot);
        statusCard.addView(statusIndicator);

        status = text(WAITING_TEXT, 16, R.color.istan_olive, false);
        status.setMaxLines(1);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(8), 0, dp(4), 0);
        statusCard.addView(status);

        LinearLayout.LayoutParams scParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scParams.setMargins(0, 0, 0, dp(12));
        header.addView(statusCard, scParams);

        pageList = new RecyclerView(activity);
        pageList.setLayoutManager(new LinearLayoutManager(activity));
        pageList.setPadding(dp(8), dp(8), dp(8), dp(8));
        pageList.setClipToPadding(false);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        outer.addView(pageList, listParams);

        final Runnable[] updateCountRef = new Runnable[1];

        if (titleText.equals("Remove/Reorder PDF") || titleText.equals("Remove Pages from DOCX") || titleText.equals("Reorder Pages from DOCX")) {
            LinearLayout selectedRow = new LinearLayout(activity);
            selectedRow.setOrientation(LinearLayout.HORIZONTAL);
            selectedRow.setGravity(Gravity.CENTER_VERTICAL);
            selectedRow.setPadding(dp(22), dp(8), dp(22), dp(8));

            LinearLayout textCol = new LinearLayout(activity);
            textCol.setOrientation(LinearLayout.VERTICAL);
            selectedRow.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView selTitle = text("Selected Pages", 18, R.color.istan_text, true);
            textCol.addView(selTitle);

            TextView selSub = text("", 14, R.color.istan_olive, false);
            selSub.setPadding(0, dp(4), 0, 0);
            textCol.addView(selSub);

            MaterialCardView editBtn = new MaterialCardView(activity);
            editBtn.setCardBackgroundColor(Color.TRANSPARENT);
            editBtn.setRadius(dp(100));
            editBtn.setStrokeWidth(dp(1));
            editBtn.setStrokeColor(color(R.color.istan_outline));
            editBtn.setCardElevation(0);
            editBtn.setRippleColorResource(android.R.color.transparent);

            LinearLayout editLayout = new LinearLayout(activity);
            editLayout.setOrientation(LinearLayout.HORIZONTAL);
            editLayout.setGravity(Gravity.CENTER_VERTICAL);
            editLayout.setPadding(dp(12), dp(6), dp(12), dp(6));

            ImageView editIcon = new ImageView(activity);
            editIcon.setImageResource(R.drawable.edit_minimal_24px);
            editIcon.setColorFilter(color(R.color.istan_text_muted));
            editLayout.addView(editIcon, new LinearLayout.LayoutParams(dp(16), dp(16)));

            TextView editTxt = text("Edit Range", 14, R.color.istan_text_muted, false);
            editTxt.setPadding(dp(6), 0, 0, 0);
            editLayout.addView(editTxt);

            editBtn.addView(editLayout);

            selectedRow.addView(editBtn);
            outer.addView(selectedRow, 2);

            updateCountRef[0] = () -> {
                int count = 0;
                for (PageItem p : actions.getPages()) if (p.keep) count++;
                selSub.setText(count + " of " + actions.getPages().size() + " pages selected");
            };
            updateCountRef[0].run();

            editBtn.setOnClickListener(v -> {
                android.widget.EditText input = new android.widget.EditText(activity);
                input.setTextColor(color(R.color.istan_text));
                input.setHint("Type range (e.g. 1-3, 5)...");
                input.setHintTextColor(color(R.color.istan_text_muted));
                input.setPadding(dp(16), dp(16), dp(16), dp(16));

                int accentColor = color(R.color.istan_olive);
                input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
                input.setHighlightColor(android.graphics.Color.argb(76, android.graphics.Color.red(accentColor), android.graphics.Color.green(accentColor), android.graphics.Color.blue(accentColor)));
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    android.graphics.drawable.Drawable cursor = input.getTextCursorDrawable();
                    if (cursor != null) { cursor.setTint(accentColor); input.setTextCursorDrawable(cursor); }
                    android.graphics.drawable.Drawable handle = input.getTextSelectHandle();
                    if (handle != null) { handle.setTint(accentColor); input.setTextSelectHandle(handle); }
                    android.graphics.drawable.Drawable handleLeft = input.getTextSelectHandleLeft();
                    if (handleLeft != null) { handleLeft.setTint(accentColor); input.setTextSelectHandleLeft(handleLeft); }
                    android.graphics.drawable.Drawable handleRight = input.getTextSelectHandleRight();
                    if (handleRight != null) { handleRight.setTint(accentColor); input.setTextSelectHandleRight(handleRight); }
                }

                List<PageItem> pages = actions.getPages();
                StringBuilder sb = new StringBuilder();
                int start = -1;
                int end = -1;
                for (PageItem p : pages) {
                    if (!p.keep) continue;
                    int num = p.originalIndex + 1;
                    if (start == -1) {
                        start = num;
                        end = num;
                    } else if (num == end + 1) {
                        end = num;
                    } else {
                        if (start == end) sb.append(start).append(",");
                        else sb.append(start).append("-").append(end).append(",");
                        start = num;
                        end = num;
                    }
                }
                if (start != -1) {
                     if (start == end) sb.append(start);
                     else sb.append(start).append("-").append(end);
                } else if (sb.length() > 0 && sb.charAt(sb.length() - 1) == ',') {
                     sb.setLength(sb.length() - 1);
                }
                input.setText(sb.toString());

                actions.onShowCustomDialog("Edit Range", input, "Cancel", null, "Apply", () -> {
                    String rangeStr = input.getText().toString();
                    List<PageItem> currentPages = actions.getPages();
                    List<Uri> pendingUris = actions.getPendingUris();
                    if (rangeStr.trim().isEmpty()) {
                        for (PageItem p : currentPages) p.keep = false;
                    } else {
                        try {
                            List<Integer> pagesToKeep = new ArrayList<>();
                            String[] parts = rangeStr.split(",");
                            for (String part : parts) {
                                String p = part.trim();
                                if (p.isEmpty()) continue;
                                if (p.contains("-")) {
                                    String[] bounds = p.split("-");
                                    if (bounds.length == 2) {
                                        int startIdx = Integer.parseInt(bounds[0].trim());
                                        int endIdx = Integer.parseInt(bounds[1].trim());
                                        if (startIdx <= endIdx) {
                                            for (int k = startIdx; k <= endIdx; k++) pagesToKeep.add(k - 1);
                                        } else {
                                            for (int k = startIdx; k >= endIdx; k--) pagesToKeep.add(k - 1);
                                        }
                                    }
                                } else {
                                    pagesToKeep.add(Integer.parseInt(p) - 1);
                                }
                            }

                            List<PageItem> newPages = new ArrayList<>();
                            List<Uri> newUris = new ArrayList<>();
                            List<Integer> processedIndices = new ArrayList<>();

                            for (int originalIdx : pagesToKeep) {
                                if (processedIndices.contains(originalIdx)) continue;
                                for (int i = 0; i < currentPages.size(); i++) {
                                    PageItem p = currentPages.get(i);
                                    if (p.originalIndex == originalIdx) {
                                        p.keep = true;
                                        newPages.add(p);
                                        if ("Merge PDF".equals(titleText) && i < pendingUris.size()) {
                                            newUris.add(pendingUris.get(i));
                                        }
                                        processedIndices.add(originalIdx);
                                        break;
                                    }
                                }
                            }

                            for (int i = 0; i < currentPages.size(); i++) {
                                PageItem p = currentPages.get(i);
                                if (!processedIndices.contains(p.originalIndex)) {
                                    p.keep = false;
                                    newPages.add(p);
                                    if ("Merge PDF".equals(titleText) && i < pendingUris.size()) {
                                        newUris.add(pendingUris.get(i));
                                    }
                                }
                            }

                            currentPages.clear();
                            currentPages.addAll(newPages);

                            if ("Merge PDF".equals(titleText)) {
                                pendingUris.clear();
                                pendingUris.addAll(newUris);
                            }

                        } catch (Exception ignored) {
                            Toast.makeText(activity, "Invalid range format", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    if (pageList.getAdapter() != null) pageList.getAdapter().notifyDataSetChanged();
                    updateCountRef[0].run();
                });
            });
        }

        boolean isRemoveDocx = titleText.equals("Remove Pages from DOCX");
        boolean hideRotate = isRemoveDocx || titleText.equals("Merge PDF");
        boolean hideDrag = isRemoveDocx;
        boolean isImg = titleText.equals("Images to PDF")
                || titleText.equals("Remove/Reorder PDF")
                || titleText.equals("Reorder Pages from DOCX")
                || isRemoveDocx
                || titleText.equals("Merge PDF");

        boolean isMerge = titleText.equals("Merge PDF");
        PagesAdapter adapter = new PagesAdapter(actions, () -> {
            if (updateCountRef[0] != null) {
                updateCountRef[0].run();
            }
        }, isImg, hideRotate, hideDrag, isMerge);
        pageList.setAdapter(adapter);

        if (allowReorder) {
            ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
                @Override
                public int getDragDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                    if (viewHolder.getItemViewType() == 1) return 0;
                    return super.getDragDirs(recyclerView, viewHolder);
                }

                @Override
                public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                    if (target.getItemViewType() == 1) return false;
                    int from = viewHolder.getBindingAdapterPosition();
                    int to = target.getBindingAdapterPosition();
                    if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                        return false;
                    }
                    List<PageItem> pages = actions.getPages();
                    PageItem item = pages.remove(from);
                    pages.add(to, item);

                    if ("Merge PDF".equals(titleText)) {
                        List<Uri> pendingUris = actions.getPendingUris();
                        Uri u = pendingUris.remove(from);
                        pendingUris.add(to, u);
                    }

                    adapter.notifyItemMoved(from, to);
                    return true;
                }

                @Override
                public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                }
            });
            touchHelper.attachToRecyclerView(pageList);
        }

        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(22), dp(8), dp(22), dp(18));

        if ("Images to PDF".equals(titleText) || "Reorder Pages from DOCX".equals(titleText) || titleText.equals("Remove/Reorder PDF") || "Merge PDF".equals(titleText)) {
            MaterialCardView addCard = new MaterialCardView(activity);
            addCard.setCardBackgroundColor(color(R.color.istan_surface));
            addCard.setRadius(dp(28));
            addCard.setCardElevation(0);

            android.graphics.drawable.GradientDrawable dashBg = new android.graphics.drawable.GradientDrawable();
            dashBg.setColor(Color.TRANSPARENT);
            dashBg.setCornerRadius(dp(28));
            dashBg.setStroke(dp(1), color(R.color.istan_outline), dp(4), dp(4));
            addCard.setBackground(dashBg);

            LinearLayout addRow = new LinearLayout(activity);
            addRow.setOrientation(LinearLayout.HORIZONTAL);
            addRow.setGravity(Gravity.CENTER);
            addRow.setPadding(dp(16), dp(12), dp(16), dp(12));
            addCard.addView(addRow);

            android.graphics.drawable.GradientDrawable circleBg = new android.graphics.drawable.GradientDrawable();
            circleBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            circleBg.setColor(color(R.color.istan_olive));

            TextView plusText = text("+", 20, R.color.istan_text, false);
            plusText.setTextColor(Color.WHITE);
            plusText.setGravity(Gravity.CENTER);
            plusText.setBackground(circleBg);
            addRow.addView(plusText, new LinearLayout.LayoutParams(dp(28), dp(28)));

            String labelStr = "Tap to Add Images / PDF";
            if ("Reorder Pages from DOCX".equals(titleText)) labelStr = "Tap to Add Images / DOCX / PDF";
            else if ("Merge PDF".equals(titleText)) labelStr = "Tap to Add PDF";
            TextView addTitle = text(labelStr, 15, R.color.istan_text, false);
            addTitle.setPadding(dp(12), 0, 0, 0);
            addRow.addView(addTitle);

            addCard.setOnClickListener(v -> actions.onAddItems(titleText));

            LinearLayout.LayoutParams acLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            acLp.setMargins(0, 0, 0, dp(12));
            footer.addView(addCard, 0, acLp);
        }

        View save = actionButton(saveLabelText, true, () -> actions.onSave(titleText, docxExport));
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveLp.gravity = Gravity.CENTER_HORIZONTAL;
        saveLp.setMargins(0, 0, 0, dp(8));
        footer.addView(save, saveLp);

        outer.addView(footer);
        return outer;
    }

    public TextView getStatus() { return status; }
    public ImageView getStatusIndicator() { return statusIndicator; }
    public RecyclerView getPageList() { return pageList; }

    

    private TextView text(String value, int sp, int colorRes, boolean bold) {
        TextView textView = new TextView(activity);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color(colorRes));
        textView.setTypeface(bold ? boldFont : regularFont);
        textView.setIncludeFontPadding(true);
        return textView;
    }

    private int color(int colorRes) {
        return ThemePrefs.resolveColor(activity, colorRes);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private View actionButton(String title, boolean primary, Runnable action) {
        MaterialCardView card = new MaterialCardView(activity);
        int cardColor = color(primary ? R.color.istan_olive : R.color.istan_surface);
        card.setCardBackgroundColor(cardColor);
        card.setRadius(dp(16));
        if (!primary) {
            card.setStrokeWidth(dp(1));
            card.setStrokeColor(color(R.color.istan_outline));
        } else {
            card.setStrokeWidth(0);
        }
        card.setCardElevation(0);
        card.setUseCompatPadding(true);

        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(22), dp(12), dp(22), dp(12));
        row.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(row);

        TextView label = text(title, 15, R.color.istan_text, false);
        label.setTextColor(primary ? ThemePrefs.contrastText(cardColor) : color(R.color.istan_text));
        label.setGravity(Gravity.CENTER);
        row.addView(label);

        card.setOnClickListener(view -> action.run());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(6));
        card.setLayoutParams(params);
        return card;
    }

    

    private class PagesAdapter extends RecyclerView.Adapter<PagesAdapter.PageViewHolder> {

        private final EditorActions actions;
        private Runnable checkboxSyncListener;
        private boolean isImg;
        private boolean hideRotate;
        private boolean hideDrag;
        private boolean isMerge;

        PagesAdapter(EditorActions actions, Runnable checkboxSyncListener, boolean isImg, boolean hideRotate, boolean hideDrag, boolean isMerge) {
            this.actions = actions;
            this.checkboxSyncListener = checkboxSyncListener;
            this.isImg = isImg;
            this.hideRotate = hideRotate;
            this.hideDrag = hideDrag;
            this.isMerge = isMerge;
        }

        class PageViewHolder extends RecyclerView.ViewHolder {
            ImageView preview;
            CheckBox keep;
            TextView info;
            TextView titleText;
            ImageView rotateLeft;
            ImageView rotateRight;
            TextView crossBtn;

            PageViewHolder(View itemView, ImageView preview, CheckBox keep, TextView info, TextView titleText, ImageView rotateLeft, ImageView rotateRight, TextView crossBtn) {
                super(itemView);
                this.preview = preview;
                this.keep = keep;
                this.info = info;
                this.titleText = titleText;
                this.rotateLeft = rotateLeft;
                this.rotateRight = rotateRight;
                this.crossBtn = crossBtn;
            }
        }

        @Override
        public int getItemCount() {
            return actions.getPages().size();
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @NonNull
        @Override
        public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

            MaterialCardView card = new MaterialCardView(activity);
            card.setCardBackgroundColor(color(R.color.istan_surface));
            card.setRadius(dp(16));
            card.setStrokeWidth(dp(1));
            card.setStrokeColor(color(R.color.istan_outline));
            card.setCardElevation(0);
            card.setUseCompatPadding(true);

            if (isImg) {
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(12), dp(20), dp(12));
                card.addView(row);

                TextView dragHandle = text("\u22EE\u22EE", 24, R.color.istan_olive, false);
                dragHandle.setPadding(dp(8), dp(4), dp(16), dp(4));
                if (hideDrag) dragHandle.setVisibility(View.GONE);
                row.addView(dragHandle);

                ImageView preview = new ImageView(activity);
                preview.setBackgroundColor(Color.WHITE);
                preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                preview.setAdjustViewBounds(true);

                FrameLayout previewFrame = new FrameLayout(activity);
                android.graphics.drawable.GradientDrawable frameBg = new android.graphics.drawable.GradientDrawable();
                frameBg.setColor(Color.WHITE);
                frameBg.setStroke(dp(1), color(R.color.istan_outline));
                previewFrame.setBackground(frameBg);
                previewFrame.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                row.addView(previewFrame, new LinearLayout.LayoutParams(dp(54), dp(72)));

                LinearLayout infoBox = new LinearLayout(activity);
                infoBox.setOrientation(LinearLayout.VERTICAL);
                infoBox.setPadding(dp(16), 0, dp(8), 0);
                row.addView(infoBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                LinearLayout titleRow = new LinearLayout(activity);
                titleRow.setOrientation(LinearLayout.HORIZONTAL);
                titleRow.setGravity(Gravity.CENTER_VERTICAL);
                titleRow.setBaselineAligned(false);
                infoBox.addView(titleRow);

                TextView titleText = text("", 15, R.color.istan_text, true);
                titleText.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                titleText.setSingleLine(true);
                titleRow.addView(titleText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                android.content.res.ColorStateList cbColors = new android.content.res.ColorStateList(
                     new int[][]{
                          new int[]{-android.R.attr.state_checked},
                          new int[]{android.R.attr.state_checked}
                     },
                     new int[]{
                          color(R.color.istan_outline),
                          color(R.color.istan_olive)
                     }
                );
                CheckBox keepBox = new CheckBox(activity);
                keepBox.setButtonTintList(cbColors);

                LinearLayout.LayoutParams cbParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cbParams.setMargins(dp(8), 0, 0, 0);
                cbParams.gravity = Gravity.CENTER_VERTICAL;

                TextView crossBtn = null;
                if (isMerge) {
                    keepBox.setVisibility(View.GONE);
                    crossBtn = new TextView(activity);
                    crossBtn.setText("\u2715");
                    crossBtn.setTextSize(22);
                    crossBtn.setTextColor(color(R.color.istan_olive));
                    crossBtn.setPadding(dp(12), dp(4), dp(8), dp(4));
                }

                TextView infoText = text("", 13, R.color.istan_olive, false);
                infoText.setPadding(0, dp(2), 0, 0);
                if (isMerge) infoText.setVisibility(View.GONE);
                infoBox.addView(infoText);

                ImageView rotateLeft = new ImageView(activity);
                rotateLeft.setImageResource(R.drawable.rotate_left);
                rotateLeft.setColorFilter(color(R.color.istan_olive_dark));
                rotateLeft.setPadding(dp(8), dp(8), dp(8), dp(8));
                if (hideRotate) rotateLeft.setVisibility(View.GONE);

                ImageView rotateRight = new ImageView(activity);
                rotateRight.setImageResource(R.drawable.rotate_right);
                rotateRight.setColorFilter(color(R.color.istan_olive_dark));
                rotateRight.setPadding(dp(8), dp(8), dp(8), dp(8));
                if (hideRotate) rotateRight.setVisibility(View.GONE);

                LinearLayout actionsBox = new LinearLayout(activity);
                actionsBox.setOrientation(LinearLayout.HORIZONTAL);
                actionsBox.setGravity(Gravity.CENTER_VERTICAL);
                actionsBox.addView(rotateLeft, new LinearLayout.LayoutParams(dp(40), dp(40)));
                actionsBox.addView(rotateRight, new LinearLayout.LayoutParams(dp(40), dp(40)));
                if (isMerge) {
                    actionsBox.addView(crossBtn);
                } else {
                    actionsBox.addView(keepBox, cbParams);
                }
                row.addView(actionsBox);

                RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, dp(8));
                card.setLayoutParams(lp);

                return new PageViewHolder(card, preview, keepBox, infoText, titleText, rotateLeft, rotateRight, crossBtn);
            } else {
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(12), dp(12), dp(12), dp(12));
                card.addView(row);

                ImageView preview = new ImageView(activity);
                preview.setBackgroundColor(Color.WHITE);
                preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                preview.setAdjustViewBounds(true);
                preview.setMaxHeight(dp(560));
                row.addView(preview, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                LinearLayout infoBox = new LinearLayout(activity);
                infoBox.setOrientation(LinearLayout.HORIZONTAL);
                infoBox.setGravity(Gravity.CENTER_VERTICAL);
                infoBox.setBaselineAligned(false);
                infoBox.setPadding(0, dp(12), 0, 0);
                row.addView(infoBox, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView infoText = text("", 16, R.color.istan_text, true);
                infoBox.addView(infoText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                ImageView rotateLeft = new ImageView(activity);
                rotateLeft.setImageResource(R.drawable.rotate_left);
                rotateLeft.setColorFilter(color(R.color.istan_olive_dark));
                rotateLeft.setPadding(dp(8), dp(8), dp(8), dp(8));
                infoBox.addView(rotateLeft, new LinearLayout.LayoutParams(dp(40), dp(40)));

                ImageView rotateRight = new ImageView(activity);
                rotateRight.setImageResource(R.drawable.rotate_right);
                rotateRight.setColorFilter(color(R.color.istan_olive_dark));
                rotateRight.setPadding(dp(8), dp(8), dp(8), dp(8));
                infoBox.addView(rotateRight, new LinearLayout.LayoutParams(dp(40), dp(40)));

                CheckBox keepBox = new CheckBox(activity);
                keepBox.setText("Keep");
                keepBox.setTextSize(18);
                keepBox.setTextColor(color(R.color.istan_text));
                keepBox.setTypeface(regularFont);
                LinearLayout.LayoutParams keepLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                keepLp.gravity = Gravity.CENTER_VERTICAL;
                infoBox.addView(keepBox, keepLp);

                RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, dp(8));
                card.setLayoutParams(lp);

                return new PageViewHolder(card, preview, keepBox, infoText, null, rotateLeft, rotateRight, null);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
            List<PageItem> pages = actions.getPages();
            PageItem item = pages.get(position);
            holder.preview.setImageBitmap(item.thumbnail);

            if (isMerge && holder.crossBtn != null) {
                holder.crossBtn.setOnClickListener(v -> {
                    int pos = holder.getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        actions.getPages().remove(pos);
                        actions.getPendingUris().remove(pos);
                        notifyItemRemoved(pos);
                        if (checkboxSyncListener != null) checkboxSyncListener.run();
                    }
                });
            }

            if (isImg) {
                if (holder.titleText != null) {
                    holder.titleText.setVisibility(View.VISIBLE);
                    holder.titleText.setText(item.displayName != null && !item.displayName.trim().isEmpty() ? item.displayName : "Page " + (item.originalIndex + 1));
                }
                holder.info.setText(item.keep ? "Selected" : "Unselected");
                holder.info.setTextColor(color(item.keep ? R.color.istan_olive : R.color.istan_text_muted));

                holder.preview.setOnClickListener(v -> {
                    android.app.Dialog dialog = new android.app.Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
                    dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                    dialog.getWindow().setStatusBarColor(Color.parseColor("#E6252525"));
                    dialog.getWindow().setNavigationBarColor(Color.parseColor("#E6252525"));
                    dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));

                    LinearLayout dialogRoot = new LinearLayout(activity);
                    dialogRoot.setOrientation(LinearLayout.VERTICAL);
                    dialogRoot.setBackgroundColor(Color.parseColor("#E6252525"));

                    FrameLayout topBar = new FrameLayout(activity);
                    TextView closeBtn = new TextView(activity);
                    closeBtn.setText("\u2715");
                    closeBtn.setTextColor(Color.WHITE);
                    closeBtn.setTextSize(26);
                    closeBtn.setPadding(dp(16), dp(12), dp(16), dp(12));
                    FrameLayout.LayoutParams clsLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    clsLp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
                    topBar.addView(closeBtn, clsLp);
                    dialogRoot.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                    FrameLayout cardContainer = new FrameLayout(activity);
                    LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
                    containerLp.setMargins(dp(12), dp(4), dp(12), dp(4));
                    dialogRoot.addView(cardContainer, containerLp);

                    MaterialCardView imgCard = new MaterialCardView(activity);
                    imgCard.setCardBackgroundColor(Color.BLACK);
                    imgCard.setRadius(dp(12));
                    imgCard.setCardElevation(0);
                    imgCard.setStrokeColor(Color.parseColor("#33FFFFFF"));
                    imgCard.setStrokeWidth(dp(1));

                    ImageView fullImg = new ImageView(activity);
                    fullImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    fullImg.setAdjustViewBounds(true);
                    imgCard.addView(fullImg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                    FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    cardLp.gravity = Gravity.CENTER;
                    cardContainer.addView(imgCard, cardLp);

                    LinearLayout bottomControls = new LinearLayout(activity);
                    bottomControls.setOrientation(LinearLayout.HORIZONTAL);
                    bottomControls.setGravity(Gravity.CENTER_VERTICAL);
                    bottomControls.setBaselineAligned(false);
                    bottomControls.setPadding(dp(24), dp(8), dp(24), dp(8));

                    TextView pageCounter = text("", 16, R.color.istan_surface, true);
                    pageCounter.setTextColor(Color.WHITE);
                    bottomControls.addView(pageCounter);

                    View spacerBottom = new View(activity);
                    bottomControls.addView(spacerBottom, new LinearLayout.LayoutParams(0, 0, 1));

                    TextView backTxt = new TextView(activity);
                    backTxt.setText("Back");
                    backTxt.setTextColor(Color.parseColor("#AAAAAA"));
                    backTxt.setTextSize(14);
                    backTxt.setPadding(0, 0, dp(16), 0);
                    bottomControls.addView(backTxt);

                    ImageView rotLeft = new ImageView(activity);
                    rotLeft.setImageResource(R.drawable.rotate_left);
                    rotLeft.setColorFilter(Color.WHITE);
                    rotLeft.setPadding(dp(8), dp(8), dp(8), dp(8));
                    if (hideRotate) rotLeft.setVisibility(View.GONE);
                    bottomControls.addView(rotLeft, new LinearLayout.LayoutParams(dp(40), dp(40)));

                    ImageView rotRight = new ImageView(activity);
                    rotRight.setImageResource(R.drawable.rotate_right);
                    rotRight.setColorFilter(Color.WHITE);
                    rotRight.setPadding(dp(8), dp(8), dp(8), dp(8));
                    if (hideRotate) rotRight.setVisibility(View.GONE);
                    bottomControls.addView(rotRight, new LinearLayout.LayoutParams(dp(40), dp(40)));

                    View sepCb = new View(activity);
                    bottomControls.addView(sepCb, new LinearLayout.LayoutParams(dp(16), 0));

                    android.content.res.ColorStateList cbColors = new android.content.res.ColorStateList(
                         new int[][]{new int[]{-android.R.attr.state_checked}, new int[]{android.R.attr.state_checked}},
                         new int[]{Color.parseColor("#888888"), color(R.color.istan_olive)}
                    );
                    CheckBox keepBox = new CheckBox(activity);
                    keepBox.setButtonTintList(cbColors);
                    keepBox.setText("Keep");
                    keepBox.setTextSize(14);
                    keepBox.setTextColor(Color.WHITE);
                    keepBox.setPadding(dp(8), 0, 0, 0);
                    LinearLayout.LayoutParams keepLpDialog = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    keepLpDialog.gravity = Gravity.CENTER_VERTICAL;
                    bottomControls.addView(keepBox, keepLpDialog);

                    dialogRoot.addView(bottomControls, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));


                    final int[] currentPos = {holder.getBindingAdapterPosition()};

                    Runnable updateUi = () -> {
                        List<PageItem> pgs = actions.getPages();
                        PageItem p = pgs.get(currentPos[0]);
                        fullImg.setImageBitmap(p.thumbnail);
                        if (p.thumbnail != null && p.thumbnail.getWidth() > p.thumbnail.getHeight()) {
                            imgCard.setContentPadding(dp(24), 0, dp(24), 0);
                        } else {
                            imgCard.setContentPadding(0, dp(48), 0, dp(48));
                        }
                        pageCounter.setText((currentPos[0] + 1) + " / " + pgs.size());
                        keepBox.setOnCheckedChangeListener(null);
                        keepBox.setChecked(p.keep);
                        keepBox.setOnCheckedChangeListener((bw, checked) -> {
                            p.keep = checked;
                            if (checkboxSyncListener != null) checkboxSyncListener.run();
                            RecyclerView.ViewHolder vh = pageList.findViewHolderForAdapterPosition(currentPos[0]);
                            if (vh instanceof PageViewHolder) {
                                ((PageViewHolder) vh).keep.setChecked(checked);
                                ((PageViewHolder) vh).info.setText(checked ? "Selected" : "Unselected");
                                ((PageViewHolder) vh).info.setTextColor(color(checked ? R.color.istan_olive : R.color.istan_text_muted));
                            }
                        });
                    };

                    updateUi.run();

                    rotLeft.setOnClickListener(x -> {
                        List<PageItem> pgs = actions.getPages();
                        PageItem p = pgs.get(currentPos[0]);
                        p.rotation = (p.rotation - 90) % 360;
                        android.graphics.Matrix matrix = new android.graphics.Matrix();
                        matrix.postRotate(-90);
                        Bitmap newThumb = Bitmap.createBitmap(p.thumbnail, 0, 0, p.thumbnail.getWidth(), p.thumbnail.getHeight(), matrix, true);
                        p.thumbnail.recycle();
                        p.thumbnail = newThumb;
                        updateUi.run();
                        RecyclerView.ViewHolder vh = pageList.findViewHolderForAdapterPosition(currentPos[0]);
                        if (vh instanceof PageViewHolder) {
                            ((PageViewHolder) vh).preview.setImageBitmap(p.thumbnail);
                        }
                    });
                    rotRight.setOnClickListener(x -> {
                        List<PageItem> pgs = actions.getPages();
                        PageItem p = pgs.get(currentPos[0]);
                        p.rotation = (p.rotation + 90) % 360;
                        android.graphics.Matrix matrix = new android.graphics.Matrix();
                        matrix.postRotate(90);
                        Bitmap newThumb = Bitmap.createBitmap(p.thumbnail, 0, 0, p.thumbnail.getWidth(), p.thumbnail.getHeight(), matrix, true);
                        p.thumbnail.recycle();
                        p.thumbnail = newThumb;
                        updateUi.run();
                        RecyclerView.ViewHolder vh = pageList.findViewHolderForAdapterPosition(currentPos[0]);
                        if (vh instanceof PageViewHolder) {
                            ((PageViewHolder) vh).preview.setImageBitmap(p.thumbnail);
                        }
                    });

                    fullImg.setOnTouchListener(new View.OnTouchListener() {
                        float startX = 0;
                        @Override
                        public boolean onTouch(View view, android.view.MotionEvent event) {
                            switch (event.getAction()) {
                                case android.view.MotionEvent.ACTION_DOWN:
                                    startX = event.getX();
                                    return true;
                                case android.view.MotionEvent.ACTION_UP:
                                    float diff = event.getX() - startX;
                                    List<PageItem> pgs = actions.getPages();
                                    if (diff > 150) {
                                        currentPos[0] = (currentPos[0] - 1 + pgs.size()) % pgs.size();
                                        updateUi.run();
                                    } else if (diff < -150) {
                                        currentPos[0] = (currentPos[0] + 1) % pgs.size();
                                        updateUi.run();
                                    } else {
                                        view.performClick();
                                    }
                                    return true;
                            }
                            return true;
                        }
                    });

                    backTxt.setOnClickListener(x -> dialog.dismiss());
                    closeBtn.setOnClickListener(x -> dialog.dismiss());
                    dialog.setContentView(dialogRoot);
                    dialog.show();
                });

            } else {
                holder.info.setText("Page " + (item.originalIndex + 1));
                holder.preview.setOnClickListener(v -> {
                    android.app.Dialog dialog = new android.app.Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
                    dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                    dialog.getWindow().setStatusBarColor(Color.parseColor("#E6252525"));
                    dialog.getWindow().setNavigationBarColor(Color.parseColor("#E6252525"));
                    dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));

                    LinearLayout dialogRoot2 = new LinearLayout(activity);
                    dialogRoot2.setOrientation(LinearLayout.VERTICAL);
                    dialogRoot2.setBackgroundColor(Color.parseColor("#E6252525"));

                    FrameLayout topBar2 = new FrameLayout(activity);
                    TextView closeBtn = new TextView(activity);
                    closeBtn.setText("\u2715");
                    closeBtn.setTextColor(Color.WHITE);
                    closeBtn.setTextSize(26);
                    closeBtn.setPadding(dp(16), dp(12), dp(16), dp(12));
                    FrameLayout.LayoutParams clsLp2 = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    clsLp2.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
                    topBar2.addView(closeBtn, clsLp2);
                    dialogRoot2.addView(topBar2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                    FrameLayout cardContainer = new FrameLayout(activity);
                    LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
                    containerLp.setMargins(dp(12), dp(4), dp(12), dp(4));
                    dialogRoot2.addView(cardContainer, containerLp);

                    MaterialCardView imgCard = new MaterialCardView(activity);
                    imgCard.setCardBackgroundColor(Color.BLACK);
                    imgCard.setRadius(dp(12));
                    imgCard.setCardElevation(0);
                    imgCard.setStrokeColor(Color.parseColor("#33FFFFFF"));
                    imgCard.setStrokeWidth(dp(1));

                    ImageView fullImg = new ImageView(activity) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                            int w = getMeasuredWidth();
                            int h = getMeasuredHeight();
                            int maxH = (int) (w * 1.5f);
                            if (h > maxH) {
                                setMeasuredDimension(w, maxH);
                            }
                        }
                    };
                    fullImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    fullImg.setAdjustViewBounds(true);
                    imgCard.addView(fullImg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                    FrameLayout.LayoutParams cardLp2 = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    cardLp2.gravity = Gravity.CENTER;
                    cardContainer.addView(imgCard, cardLp2);

                    LinearLayout bottomControls = new LinearLayout(activity);
                    bottomControls.setOrientation(LinearLayout.HORIZONTAL);
                    bottomControls.setGravity(Gravity.CENTER_VERTICAL);
                    bottomControls.setBaselineAligned(false);
                    bottomControls.setPadding(dp(24), dp(8), dp(24), dp(8));

                    TextView pageCounter = text("", 16, R.color.istan_surface, true);
                    pageCounter.setTextColor(Color.WHITE);
                    bottomControls.addView(pageCounter);

                    View spacerBottom = new View(activity);
                    bottomControls.addView(spacerBottom, new LinearLayout.LayoutParams(0, 0, 1));

                    TextView backTxt = new TextView(activity);
                    backTxt.setText("Back");
                    backTxt.setTextColor(Color.parseColor("#AAAAAA"));
                    backTxt.setTextSize(14);
                    backTxt.setPadding(0, 0, dp(16), 0);
                    bottomControls.addView(backTxt);

                    ImageView rotLeft = new ImageView(activity);
                    rotLeft.setImageResource(R.drawable.rotate_left);
                    rotLeft.setColorFilter(Color.WHITE);
                    rotLeft.setPadding(dp(8), dp(8), dp(8), dp(8));
                    if (hideRotate) rotLeft.setVisibility(View.GONE);
                    bottomControls.addView(rotLeft, new LinearLayout.LayoutParams(dp(40), dp(40)));

                    ImageView rotRight = new ImageView(activity);
                    rotRight.setImageResource(R.drawable.rotate_right);
                    rotRight.setColorFilter(Color.WHITE);
                    rotRight.setPadding(dp(8), dp(8), dp(8), dp(8));
                    if (hideRotate) rotRight.setVisibility(View.GONE);
                    bottomControls.addView(rotRight, new LinearLayout.LayoutParams(dp(40), dp(40)));

                    View sepCb = new View(activity);
                    bottomControls.addView(sepCb, new LinearLayout.LayoutParams(dp(16), 0));

                    android.content.res.ColorStateList cbColors2 = new android.content.res.ColorStateList(
                         new int[][]{new int[]{-android.R.attr.state_checked}, new int[]{android.R.attr.state_checked}},
                         new int[]{Color.parseColor("#888888"), color(R.color.istan_olive)}
                    );
                    CheckBox keepBox = new CheckBox(activity);
                    keepBox.setButtonTintList(cbColors2);
                    keepBox.setText("Keep");
                    keepBox.setTextSize(14);
                    keepBox.setTextColor(Color.WHITE);
                    keepBox.setPadding(dp(8), 0, 0, 0);
                    if (actions.getPages().size() <= 1) keepBox.setVisibility(View.GONE);
                    LinearLayout.LayoutParams keepLpDialog2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    keepLpDialog2.gravity = Gravity.CENTER_VERTICAL;
                    bottomControls.addView(keepBox, keepLpDialog2);

                    dialogRoot2.addView(bottomControls, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                    final int[] currentPos = {holder.getBindingAdapterPosition()};

                    Runnable updateUi = () -> {
                        List<PageItem> pgs = actions.getPages();
                        PageItem p = pgs.get(currentPos[0]);
                        fullImg.setImageBitmap(p.thumbnail);
                        if (p.thumbnail != null && p.thumbnail.getWidth() > p.thumbnail.getHeight()) {
                            imgCard.setContentPadding(dp(24), 0, dp(24), 0);
                        } else {
                            imgCard.setContentPadding(0, dp(48), 0, dp(48));
                        }
                        pageCounter.setText((currentPos[0] + 1) + " / " + pgs.size());
                        keepBox.setOnCheckedChangeListener(null);
                        keepBox.setChecked(p.keep);
                        keepBox.setOnCheckedChangeListener((bw, checked) -> {
                            p.keep = checked;
                            if (checkboxSyncListener != null) checkboxSyncListener.run();
                            RecyclerView.ViewHolder vh = pageList.findViewHolderForAdapterPosition(currentPos[0]);
                            if (vh instanceof PageViewHolder) {
                                ((PageViewHolder) vh).keep.setChecked(checked);
                            }
                        });
                    };

                    updateUi.run();

                    rotLeft.setOnClickListener(x -> {
                        List<PageItem> pgs = actions.getPages();
                        PageItem p = pgs.get(currentPos[0]);
                        p.rotation = (p.rotation - 90) % 360;
                        android.graphics.Matrix matrix = new android.graphics.Matrix();
                        matrix.postRotate(-90);
                        Bitmap newThumb = Bitmap.createBitmap(p.thumbnail, 0, 0, p.thumbnail.getWidth(), p.thumbnail.getHeight(), matrix, true);
                        p.thumbnail.recycle();
                        p.thumbnail = newThumb;
                        updateUi.run();
                        RecyclerView.ViewHolder vh = pageList.findViewHolderForAdapterPosition(currentPos[0]);
                        if (vh instanceof PageViewHolder) {
                            ((PageViewHolder) vh).preview.setImageBitmap(p.thumbnail);
                        }
                    });
                    rotRight.setOnClickListener(x -> {
                        List<PageItem> pgs = actions.getPages();
                        PageItem p = pgs.get(currentPos[0]);
                        p.rotation = (p.rotation + 90) % 360;
                        android.graphics.Matrix matrix = new android.graphics.Matrix();
                        matrix.postRotate(90);
                        Bitmap newThumb = Bitmap.createBitmap(p.thumbnail, 0, 0, p.thumbnail.getWidth(), p.thumbnail.getHeight(), matrix, true);
                        p.thumbnail.recycle();
                        p.thumbnail = newThumb;
                        updateUi.run();
                        RecyclerView.ViewHolder vh = pageList.findViewHolderForAdapterPosition(currentPos[0]);
                        if (vh instanceof PageViewHolder) {
                            ((PageViewHolder) vh).preview.setImageBitmap(p.thumbnail);
                        }
                    });

                    fullImg.setOnTouchListener(new View.OnTouchListener() {
                        float startX = 0;
                        @Override
                        public boolean onTouch(View vw, android.view.MotionEvent event) {
                            switch (event.getAction()) {
                                case android.view.MotionEvent.ACTION_DOWN:
                                    startX = event.getX();
                                    return true;
                                case android.view.MotionEvent.ACTION_UP:
                                    float diff = event.getX() - startX;
                                    List<PageItem> pgs = actions.getPages();
                                    if (diff > 150) {
                                        currentPos[0] = (currentPos[0] - 1 + pgs.size()) % pgs.size();
                                        updateUi.run();
                                    } else if (diff < -150) {
                                        currentPos[0] = (currentPos[0] + 1) % pgs.size();
                                        updateUi.run();
                                    } else {
                                        vw.performClick();
                                    }
                                    return true;
                            }
                            return true;
                        }
                    });

                    backTxt.setOnClickListener(x -> dialog.dismiss());
                    closeBtn.setOnClickListener(x -> dialog.dismiss());
                    dialog.setContentView(dialogRoot2);
                    dialog.show();
                });
            }

            holder.keep.setOnCheckedChangeListener(null);
            holder.keep.setChecked(item.keep);
            holder.keep.setOnCheckedChangeListener((bw, checked) -> {
                item.keep = checked;
                if (isImg && holder.info != null) {
                    holder.info.setText(checked ? "Selected" : "Unselected");
                    holder.info.setTextColor(color(checked ? R.color.istan_olive : R.color.istan_text_muted));
                }
                if (checkboxSyncListener != null) {
                    checkboxSyncListener.run();
                }
            });

            if (isImg) {
                holder.keep.setVisibility(View.VISIBLE);
                if (hideRotate) {
                    holder.rotateLeft.setVisibility(View.GONE);
                    holder.rotateRight.setVisibility(View.GONE);
                } else {
                    holder.rotateLeft.setVisibility(View.VISIBLE);
                    holder.rotateRight.setVisibility(View.VISIBLE);
                }
            } else {
                holder.keep.setVisibility(actions.getPages().size() <= 1 ? View.GONE : View.VISIBLE);
                if (hideRotate) {
                    holder.rotateLeft.setVisibility(View.GONE);
                    holder.rotateRight.setVisibility(View.GONE);
                } else {
                    holder.rotateLeft.setVisibility(View.VISIBLE);
                    holder.rotateRight.setVisibility(View.VISIBLE);
                }
            }

            holder.rotateLeft.setOnClickListener(v -> {
                item.rotation = (item.rotation - 90) % 360;
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                matrix.postRotate(-90);
                Bitmap newThumb = Bitmap.createBitmap(item.thumbnail, 0, 0, item.thumbnail.getWidth(), item.thumbnail.getHeight(), matrix, true);
                item.thumbnail.recycle();
                item.thumbnail = newThumb;
                holder.preview.setImageBitmap(item.thumbnail);
            });
            holder.rotateRight.setOnClickListener(v -> {
                item.rotation = (item.rotation + 90) % 360;
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                matrix.postRotate(90);
                Bitmap newThumb = Bitmap.createBitmap(item.thumbnail, 0, 0, item.thumbnail.getWidth(), item.thumbnail.getHeight(), matrix, true);
                item.thumbnail.recycle();
                item.thumbnail = newThumb;
                holder.preview.setImageBitmap(item.thumbnail);
            });
        }

    }
}
