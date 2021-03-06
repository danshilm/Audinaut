/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package net.nullsum.audinaut.service.parser;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import net.nullsum.audinaut.R;
import net.nullsum.audinaut.domain.Artist;
import net.nullsum.audinaut.domain.Indexes;
import net.nullsum.audinaut.domain.MusicDirectory;
import net.nullsum.audinaut.util.Constants;
import net.nullsum.audinaut.util.ProgressListener;
import net.nullsum.audinaut.util.Util;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Sindre Mehus
 */
public class IndexesParser extends MusicDirectoryEntryParser {
    private static final String TAG = IndexesParser.class.getSimpleName();

    public IndexesParser(Context context, int instance) {
        super(context, instance);
    }

    public Indexes parse(InputStream inputStream, ProgressListener progressListener) throws Exception {
        long t0 = System.currentTimeMillis();
        init(inputStream);

        List<Artist> artists = new ArrayList<>();
        List<Artist> shortcuts = new ArrayList<>();
        List<MusicDirectory.Entry> entries = new ArrayList<>();
        int eventType;
        String index = "#";
        String ignoredArticles = null;
        boolean changed = false;
        Map<String, Artist> artistList = new HashMap<>();

        do {
            eventType = nextParseEvent();
            if (eventType == XmlPullParser.START_TAG) {
                String name = getElementName();
                switch (name) {
                    case "indexes":
                    case "artists":
                        changed = true;
                        ignoredArticles = get("ignoredArticles");
                        break;
                    case "index":
                        index = get("name");

                        break;
                    case "artist":
                        Artist artist = new Artist();
                        artist.setId(get("id"));
                        artist.setName(get("name"));
                        artist.setIndex(index);

                        // Combine the id's for the two artists
                        if (artistList.containsKey(artist.getName())) {
                            Artist originalArtist = artistList.get(artist.getName());
                            originalArtist.setId(originalArtist.getId() + ";" + artist.getId());
                        } else {
                            artistList.put(artist.getName(), artist);
                            artists.add(artist);
                        }

                        if (artists.size() % 10 == 0) {
                            String msg = getContext().getResources().getString(R.string.parser_artist_count, artists.size());
                            updateProgress(progressListener, msg);
                        }
                        break;
                    case "shortcut":
                        Artist shortcut = new Artist();
                        shortcut.setId(get("id"));
                        shortcut.setName(get("name"));
                        shortcut.setIndex("*");
                        shortcuts.add(shortcut);
                        break;
                    case "child":
                        MusicDirectory.Entry entry = parseEntry("");
                        entries.add(entry);
                        break;
                    case "error":
                        handleError();
                        break;
                }
            }
        } while (eventType != XmlPullParser.END_DOCUMENT);

        validate();

        if (ignoredArticles != null) {
            SharedPreferences.Editor prefs = Util.getPreferences(context).edit();
            prefs.putString(Constants.CACHE_KEY_IGNORE, ignoredArticles);
            prefs.apply();
        }

        if (!changed) {
            return null;
        }

        long t1 = System.currentTimeMillis();
        Log.d(TAG, "Got " + artists.size() + " artist(s) in " + (t1 - t0) + "ms.");

        String msg = getContext().getResources().getString(R.string.parser_artist_count, artists.size());
        updateProgress(progressListener, msg);

        return new Indexes(shortcuts, artists, entries);
    }
}
