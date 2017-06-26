package ivonhoe.android.themedemo;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * @author Ivonhoe on 2017/6/22.
 */

public class BubbleTextView extends RelativeLayout {

    public BubbleTextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        setupAttribute(context, attrs);
    }

    public BubbleTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        setupAttribute(context, attrs);
    }

    private void setupAttribute(Context context, AttributeSet attrs) {
        final TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.BubbleTabView);
        Drawable selectedTabDrawable = typedArray.getDrawable(R.styleable.BubbleTabView_bt_selectedIcon);
        Drawable unSelectedTabDrawable = typedArray.getDrawable(R.styleable.BubbleTabView_bt_unSelectedIcon);
        int selectedTextColor = typedArray.getColor(R.styleable.BubbleTabView_bt_selectedColor, -1);
        int unSelectedTextColor = typedArray.getColor(R.styleable.BubbleTabView_bt_unSelectedColor, -1);

        int index = typedArray.getInteger(R.styleable.BubbleTabView_bt_index, -1);
        typedArray.recycle();
    }
}
