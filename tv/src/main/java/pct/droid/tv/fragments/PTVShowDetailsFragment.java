package pct.droid.tv.fragments;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v17.leanback.widget.AbstractDetailsDescriptionPresenter;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.OnActionClickedListener;
import android.support.v17.leanback.widget.PresenterSelector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import pct.droid.base.content.preferences.Prefs;
import pct.droid.base.providers.media.EZTVProvider;
import pct.droid.base.providers.media.MediaProvider;
import pct.droid.base.providers.media.models.Episode;
import pct.droid.base.providers.media.models.Media;
import pct.droid.base.providers.media.models.Show;
import pct.droid.base.providers.subs.SubsProvider;
import pct.droid.base.torrent.StreamInfo;
import pct.droid.base.utils.PrefUtils;
import pct.droid.tv.R;
import pct.droid.tv.activities.PTVStreamLoadingActivity;
import pct.droid.tv.presenters.ShowDetailsDescriptionPresenter;
import pct.droid.tv.presenters.showdetail.EpisodeCardPresenter;
import pct.droid.tv.presenters.showdetail.SeasonEpisodeRow;

public class PTVShowDetailsFragment extends PTVBaseDetailsFragment
        implements MediaProvider.Callback,
        OnActionClickedListener,
        EpisodeCardPresenter.Listener {

    private EZTVProvider mTvProvider = new EZTVProvider();
    private ArrayObjectAdapter seasonAdapter;

    public static Fragment newInstance(Media media, String hero) {
        PTVShowDetailsFragment fragment = new PTVShowDetailsFragment();

        Bundle bundle = new Bundle();
        bundle.putParcelable(EXTRA_ITEM, media);
        bundle.putString(EXTRA_HERO_URL, hero);

        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    void loadDetails() {
        ArrayList<Media> mediaList = new ArrayList<>();
        mediaList.add(getShowItem());
        mTvProvider.getDetail(mediaList, 0, this);
    }

    @Override
    AbstractDetailsDescriptionPresenter getDetailPresenter() {
        return new ShowDetailsDescriptionPresenter();
    }

    @Override
    void onDetailLoaded() {
        updateSeasonAdapter();
    }

    @Override
    ClassPresenterSelector createPresenters(ClassPresenterSelector selector) {
        selector.addClassPresenter(SeasonEpisodeRow.class, new EpisodeCardPresenter(getActivity()));
        return null;
    }

    @Override
    void addActions(Media item) { }

    @Override
    protected ArrayObjectAdapter createAdapter(PresenterSelector selector) {
        this.seasonAdapter = new ArrayObjectAdapter(selector);
        return seasonAdapter;
    }

    @Override
    public void onActionClicked(Action action) {
        //no actions yet
    }

    @Override
    public void onEpisodeClicked(Episode episode) {
        if (null == episode) {
            return;
        }

        // start first torrent
        if (episode.torrents.size() == 1) {
            List<Map.Entry<String, Media.Torrent>> torrents = new ArrayList<>(
                episode.torrents.entrySet());
            onTorrentSelected(episode, torrents.get(0));
        }
        // ask user which torrent
        else {
            showTorrentsDialog(episode, episode
                .torrents);
        }
    }

    private void updateSeasonAdapter() {
        final TreeMap<Integer, List<Episode>> seasons = new TreeMap<>(new Comparator<Integer>() {
            @Override
            public int compare(Integer me, Integer other) {
                return me - other;
            }
        });

        for (Episode episode : getShowItem().episodes) {
            // create list of season if does not exists
            if (!seasons.containsKey(episode.season)) {
                seasons.put(episode.season, new ArrayList<Episode>());
            }

            // add episode to the list
            final List<Episode> seasonEpisodes = seasons.get(episode.season);
            seasonEpisodes.add(episode);
        }

        for (Integer seasonKey : seasons.descendingKeySet()) {
            Collections.sort(seasons.get(seasonKey), new Comparator<Episode>() {
                @Override
                public int compare(Episode me, Episode other) {
                    if (me.episode < other.episode) return -1;
                    else if (me.episode > other.episode) return 1;
                    return 0;
                }
            });

            EpisodeCardPresenter presenter = new EpisodeCardPresenter(getActivity());
            presenter.setOnClickListener(this);
            ArrayObjectAdapter episodes = new ArrayObjectAdapter(presenter);

            for (Episode episode : seasons.get(seasonKey)) {
                episodes.add(episode);
            }
            HeaderItem header = new HeaderItem(seasonKey, String.format("Season %d", seasonKey));
            seasonAdapter.add(new ListRow(header, episodes));
        }

        seasonAdapter.notifyArrayItemRangeChanged(0, seasonAdapter.size());
    }

    private Show getShowItem() {
        return (Show) getMediaItem();
    }

    @SuppressWarnings("unchecked")
    private void showTorrentsDialog(final Episode episode, final Map<String, Media.Torrent> torrents) {
        ArrayList<String> choices = new ArrayList<>(torrents.keySet());
        final ArrayList torrent = new ArrayList(torrents.entrySet());
        new AlertDialog.Builder(getActivity())
                .setTitle(getString(R.string.choose_quality))
                .setSingleChoiceItems(choices.toArray(new CharSequence[choices.size()]), 0, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onTorrentSelected(episode, (Map.Entry<String, Media.Torrent>) torrent.get(which));
                        dialog.dismiss();
                    }
                }).show();
    }

    private void onTorrentSelected(Episode episode, Map.Entry<String, Media.Torrent> torrent) {
        String subtitleLanguage = PrefUtils.get(
            getActivity(),
            Prefs.SUBTITLE_DEFAULT,
            SubsProvider.SUBTITLE_LANGUAGE_NONE);
        StreamInfo info = new StreamInfo(
                episode,
                getShowItem(),
                torrent.getValue().url,
                subtitleLanguage,
                torrent.getKey());

        PTVStreamLoadingActivity.startActivity(getActivity(), info);
    }

}
