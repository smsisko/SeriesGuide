
/*
 * Copyright 2014 Uwe Trottmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.battlelancer.seriesguide.adapters;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.battlelancer.seriesguide.R;
import com.battlelancer.seriesguide.ui.EpisodesFragment.EpisodesQuery;
import com.battlelancer.seriesguide.util.EpisodeTools;
import com.battlelancer.seriesguide.util.TimeTools;
import com.battlelancer.seriesguide.widgets.WatchedBox;
import com.uwetrottmann.androidutils.CheatSheet;
import java.util.Date;

public class EpisodesAdapter extends CursorAdapter {

    private static final int LAYOUT = R.layout.item_episode;

    private LayoutInflater mLayoutInflater;

    private PopupMenuClickListener mPopupMenuClickListener;

    private OnFlagEpisodeListener mOnFlagListener;

    public interface OnFlagEpisodeListener {
        public void onFlagEpisodeWatched(int episodeId, int episodeNumber, boolean isWatched);
    }

    public interface PopupMenuClickListener {
        public void onPopupMenuClick(View v, int episodeTvdbId, int episodeNumber,
                long releaseTimeMs, int watchedFlag, boolean isCollected);
    }

    public EpisodesAdapter(Context context, Cursor c, int flags, PopupMenuClickListener listener,
            OnFlagEpisodeListener flagListener) {
        super(context, c, flags);
        mLayoutInflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPopupMenuClickListener = listener;
        mOnFlagListener = flagListener;
    }

    /**
     * Get the item position in the data set, or the position of the first item if it is not found.
     */
    public int getItemPosition(long itemId) {
        Cursor cursor = getCursor();
        if (cursor != null) {
            int rowId = cursor.getColumnIndexOrThrow("_id");
            for (int position = 0; position < cursor.getCount(); position++) {
                if (!cursor.moveToPosition(position)) {
                    return 0;
                }
                if (cursor.getLong(rowId) == itemId) {
                    return position;
                }
            }
        }

        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (!mDataValid) {
            throw new IllegalStateException("this should only be called when the cursor is valid");
        }
        if (!mCursor.moveToPosition(position)) {
            throw new IllegalStateException("couldn't move cursor to position " + position);
        }

        final ViewHolder viewHolder;

        if (convertView == null) {
            convertView = newView(mContext, mCursor, parent);

            viewHolder = new ViewHolder();
            viewHolder.watchedBox = (WatchedBox) convertView
                    .findViewById(R.id.watchedBoxEpisode);
            viewHolder.episodeTitle = (TextView) convertView
                    .findViewById(R.id.textViewEpisodeTitle);
            viewHolder.episodeNumber = (TextView) convertView
                    .findViewById(R.id.textViewEpisodeNumber);
            viewHolder.episodeAirdate = (TextView) convertView
                    .findViewById(R.id.textViewEpisodeAirdate);
            viewHolder.episodeAlternativeNumbers = (TextView) convertView
                    .findViewById(R.id.textViewEpisodeAlternativeNumbers);
            viewHolder.collected = (ImageView) convertView
                    .findViewById(R.id.imageViewCollected);
            viewHolder.contextMenu = (ImageView) convertView
                    .findViewById(R.id.imageViewContextMenu);

            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        // episode title
        viewHolder.episodeTitle.setText(mCursor.getString(EpisodesQuery.TITLE));

        // number
        final int episodeNumber = mCursor.getInt(EpisodesQuery.NUMBER);
        viewHolder.episodeNumber.setText(String.valueOf(episodeNumber));

        // watched box
        viewHolder.watchedBox.setEpisodeFlag(mCursor.getInt(EpisodesQuery.WATCHED));
        final int episodeId = mCursor.getInt(EpisodesQuery._ID);
        viewHolder.watchedBox.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                WatchedBox box = (WatchedBox) v;
                // disable button, will be re-enabled on data reload once action completes
                box.setEnabled(false);
                mOnFlagListener.onFlagEpisodeWatched(episodeId, episodeNumber,
                        !EpisodeTools.isWatched(box.getEpisodeFlag()));
            }
        });
        viewHolder.watchedBox.setEnabled(true);
        final int watchedFlag = viewHolder.watchedBox.getEpisodeFlag();
        CheatSheet.setup(viewHolder.watchedBox,
                EpisodeTools.isWatched(watchedFlag) ? R.string.action_unwatched : R.string.action_watched
        );

        // collected tag
        final boolean isCollected = mCursor.getInt(EpisodesQuery.COLLECTED) == 1;
        viewHolder.collected.setVisibility(isCollected ? View.VISIBLE : View.INVISIBLE);

        // alternative numbers
        StringBuilder altNumbers = new StringBuilder();
        int absoluteNumber = mCursor.getInt(EpisodesQuery.ABSOLUTE_NUMBER);
        if (absoluteNumber > 0) {
            altNumbers.append(mContext.getString(R.string.episode_number_absolute)).append(" ")
                    .append(absoluteNumber);
        }
        double dvdNumber = mCursor.getDouble(EpisodesQuery.DVDNUMBER);
        if (dvdNumber > 0) {
            if (altNumbers.length() != 0) {
                altNumbers.append(" | ");
            }
            altNumbers.append(mContext.getString(R.string.episode_number_disk)).append(" ")
                    .append(dvdNumber);
        }
        viewHolder.episodeAlternativeNumbers.setText(altNumbers);

        // release time
        final long releaseTime = mCursor.getLong(EpisodesQuery.FIRSTAIREDMS);
        if (releaseTime != -1) {
            Date actualRelease = TimeTools.applyUserOffset(mContext, releaseTime);
            // "in 15 mins" or "15 July 2001"
            viewHolder.episodeAirdate
                    .setText(TimeTools.formatToLocalRelativeTime(mContext, actualRelease));
        } else {
            viewHolder.episodeAirdate.setText(mContext
                    .getString(R.string.episode_firstaired_unknown));
        }

        // context menu
        viewHolder.contextMenu.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPopupMenuClickListener != null) {
                    mPopupMenuClickListener.onPopupMenuClick(v, episodeId, episodeNumber,
                            releaseTime, watchedFlag, isCollected);
                }
            }
        });

        return convertView;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return mLayoutInflater.inflate(LAYOUT, parent, false);
    }

    static class ViewHolder {
        TextView episodeAlternativeNumbers;
        TextView episodeAirdate;
        TextView episodeNumber;
        TextView episodeTitle;
        WatchedBox watchedBox;
        ImageView collected;
        ImageView contextMenu;
    }
}
