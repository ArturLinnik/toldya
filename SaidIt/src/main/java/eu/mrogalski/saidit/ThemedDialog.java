package eu.mrogalski.saidit;

import android.app.Dialog;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;

import androidx.fragment.app.DialogFragment;

import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

public class ThemedDialog extends DialogFragment {
    static final String TAG = ThemedDialog.class.getName();

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        ShapeAppearanceModel shapeModel = new ShapeAppearanceModel.Builder()
                .setAllCornerSizes(28f * getResources().getDisplayMetrics().density)
                .build();
        MaterialShapeDrawable background = new MaterialShapeDrawable(shapeModel);
        background.setFillColor(null);
        dialog.getWindow().setBackgroundDrawable(background);

        return dialog;
    }
}
