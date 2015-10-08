package de.k3b.android.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import de.k3b.android.androFotoFinder.R;

/**
 * Helper Dialogs
 *
 * Created by k3b on 05.10.2015.
 */
public abstract class Dialogs {
	// showStringPicker(this, "Open query", "FileName1.query", "FileName2.query");
	public void pickFromStrings(final Activity parent, String title, final int idContextMenu, final String... strings) {
		AlertDialog.Builder builder = new AlertDialog.Builder(parent);
		builder.setTitle(title);

		builder.setItems(strings, new DialogInterface.OnClickListener() {
	        // when an item is clicked, notify our interface "onDialogResult"
	    	public void onClick(DialogInterface d, int position) {
				d.dismiss();
				onDialogResult(strings[position], strings);
			}
		});

		builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
			// when user clicks cancel, notify our interface
			public void onClick(DialogInterface d, int n) {
				d.dismiss();
				onDialogResult(null, 0);
			}
		});

		final AlertDialog dialog = builder.create();
		if (idContextMenu != 0) {
			dialog.getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
				@Override
				public boolean onItemLongClick(final AdapterView<?> listView, View view, final int position, long id) {
					MenuInflater inflater = parent.getMenuInflater();
                    view.setFocusable(true);
                    view.setFocusableInTouchMode(true);
					PopupMenu menu = new PopupMenu(parent, view);

					inflater.inflate(idContextMenu, menu.getMenu());

					menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
						@Override
						public boolean onMenuItemClick(MenuItem item) {
							final boolean result = onContextMenuItemClick(item.getItemId(), position, strings);
							dialog.dismiss();
							return result;
						}
					});
					menu.show();

					return false;
				}
			});
		}

		dialog.show();
	}

	public void editFileName(Activity parent, String title, String name, final Object... parameters) {
		AlertDialog.Builder builder = new AlertDialog.Builder(parent);
		builder.setTitle(title); // R.string.cmd_save_bookmark_as);
		View content = parent.getLayoutInflater().inflate(R.layout.dialog_edit_name, null);

		final EditText edit = (EditText) content.findViewById(R.id.edName);
		edit.setText(name);

		// select text without extension
		int selectLen = name.lastIndexOf(".");
		if (selectLen == -1) selectLen = name.length();
		edit.setSelection(0, selectLen);

		builder.setView(content);
		builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onDialogResult(null);
                dialog.dismiss();
            }
        });
		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onDialogResult(edit.getText().toString(), parameters);
                dialog.dismiss();
            }
        });
		AlertDialog alertDialog = builder.create();
		alertDialog.show();

		fixLayout(alertDialog, edit);
		setEditFocus(alertDialog, edit);
	}

    private void setEditFocus(AlertDialog alertDialog, EditText edit) {
		edit.requestFocus();

		// request keyboard. See http://stackoverflow.com/questions/2403632/android-show-soft-keyboard-automatically-when-focus-is-on-an-edittext
		alertDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
	}

	private void fixLayout(Dialog alertDialog, TextView edit) {
		int width = (int) (8 * edit.getTextSize());
		// DisplayMetrics metrics = getResources().getDisplayMetrics();
		// int width = metrics.widthPixels;
		alertDialog.getWindow().setLayout(width * 2, LinearLayout.LayoutParams.WRAP_CONTENT);
	}

	/** can be overwritten to handle context menu */
	protected boolean onContextMenuItemClick(int menuItemId, int itemIndex, String[] items) {
		return false;
	}

	/** must be overwritten to implement dialog result. null==canceled */
	abstract protected void onDialogResult(String result, Object... parameters);

    public void yesNoQuestion(Activity parent, final String title, String question, final Object... parameters) {
        AlertDialog.Builder builder = new AlertDialog.Builder(parent);
        builder.setTitle(title);
        final TextView textView = new TextView(parent);
        textView.setText(question);
        builder.setView(textView);
        builder.setNegativeButton(R.string.btn_no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onDialogResult(null);
                dialog.dismiss();
            }
        });
        builder.setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onDialogResult(title, parameters);
                dialog.dismiss();
            }
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();

        fixLayout(alertDialog, textView);
    }
}
