package io.noties.markwon.editor;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Implementation of TextWatcher that uses {@link MarkwonEditor#process(Editable)} method
 * to apply markdown highlighting right after text changes.
 *
 * @see MarkwonEditor#process(Editable)
 * @see MarkwonEditor#preRender(Editable, MarkwonEditor.PreRenderResultListener)
 * @since 4.2.0-SNAPSHOT
 */
public abstract class MarkwonEditorTextWatcher implements TextWatcher {

    @NonNull
    public static MarkwonEditorTextWatcher withProcess(@NonNull MarkwonEditor editor) {
        return new WithProcess(editor);
    }

    @NonNull
    public static MarkwonEditorTextWatcher withPreRender(
            @NonNull MarkwonEditor editor,
            @NonNull ExecutorService executorService,
            @NonNull EditText editText) {
        return new WithPreRender(editor, executorService, editText);
    }

    @Override
    public abstract void afterTextChanged(Editable s);

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }


    static class WithProcess extends MarkwonEditorTextWatcher {

        private final MarkwonEditor editor;

        private boolean selfChange;

        public WithProcess(@NonNull MarkwonEditor editor) {
            this.editor = editor;
        }

        @Override
        public void afterTextChanged(Editable s) {

            if (selfChange) {
                return;
            }

            selfChange = true;
            try {
                editor.process(s);
            } finally {
                selfChange = false;
            }
        }
    }

    static class WithPreRender extends MarkwonEditorTextWatcher {

        private final MarkwonEditor editor;
        private final ExecutorService executorService;

        @Nullable
        private EditText editText;

        private Future<?> future;

        WithPreRender(
                @NonNull MarkwonEditor editor,
                @NonNull ExecutorService executorService,
                @NonNull EditText editText) {
            this.editor = editor;
            this.executorService = executorService;
            this.editText = editText;
            this.editText.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {

                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    WithPreRender.this.editText = null;
                }
            });
        }

        @Override
        public void afterTextChanged(final Editable s) {

            // todo: maybe checking hash is not so performant?
            //   what if we create a atomic reference and use it (with tag applied to editText)?

            if (future != null) {
                future.cancel(true);
            }

            future = executorService.submit(new Runnable() {
                @Override
                public void run() {
                    editor.preRender(s, new MarkwonEditor.PreRenderResultListener() {
                        @Override
                        public void onPreRenderResult(@NonNull final MarkwonEditor.PreRenderResult result) {
                            if (editText != null) {
                                final int key = result.resultEditable().toString().hashCode();
                                editText.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (key == editText.getText().toString().hashCode()) {
                                            result.dispatchTo(editText.getText());
                                        }
                                    }
                                });
                            }
                        }
                    });
                }
            });
        }
    }
}