package com.bignerdranch.android.photogallery;

import java.util.ArrayList;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;

public class PhotoGalleryFragment extends Fragment {
    GridView mGridView;
    ArrayList<GalleryItem> mItems;
    ThumbnailDownloader<ImageView> mThumbnailThread;

    //增加缓存功能
    LruCache<String, Bitmap> mBitmapCache;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
        new FetchItemsTask().execute();

        //增加缓存功能
        int cacheSize = 4 * 1024 * 1024; //4M
        mBitmapCache = new LruCache<String, Bitmap>(cacheSize);

//        mThumbnailThread = new ThumbnailDownloader<ImageView>(new Handler());
        mThumbnailThread = new ThumbnailDownloader<ImageView>(new Handler(), mBitmapCache);
        mThumbnailThread.setListener(new ThumbnailDownloader.Listener<ImageView>() {
            public void onThumbnailDownloaded(ImageView imageView, Bitmap thumbnail) {
                if (isVisible()) {
                    imageView.setImageBitmap(thumbnail);
                }
            }
        });
        mThumbnailThread.start();
        mThumbnailThread.getLooper();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_photo_gallery, container, false);

        mGridView = (GridView) v.findViewById(R.id.gridView);

        setupAdapter();

        return v;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mThumbnailThread.quit();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mThumbnailThread.clearQueue();
    }

    void setupAdapter() {
        if (getActivity() == null || mGridView == null) return;

        if (mItems != null) {
            mGridView.setAdapter(new GalleryItemAdapter(mItems));
        } else {
            mGridView.setAdapter(null);
        }
    }

    private class FetchItemsTask extends AsyncTask<Void, Void, ArrayList<GalleryItem>> {
        @Override
        protected ArrayList<GalleryItem> doInBackground(Void... params) {
            return new FlickrFetchr().fetchItems();
        }

        @Override
        protected void onPostExecute(ArrayList<GalleryItem> items) {
            mItems = items;
            setupAdapter();
        }
    }

    private class GalleryItemAdapter extends ArrayAdapter<GalleryItem> {
        //        public GalleryItemAdapter(ArrayList<GalleryItem> items) {
//            super(getActivity(), 0, items);
//        }
        /*有加载前后十个的缓存*/
        public GalleryItemAdapter(ArrayList<GalleryItem> items) {
            super(getActivity(), android.R.layout.simple_gallery_item, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getActivity().getLayoutInflater()
                        .inflate(R.layout.gallery_item, parent, false);
            }

            GalleryItem item = getItem(position);
            ImageView imageView = (ImageView) convertView
                    .findViewById(R.id.gallery_item_imageView);
            imageView.setImageResource(R.drawable.brian_up_close);
            mThumbnailThread.queueThumbnail(imageView, item.getUrl());

            //前后十个缓存
            String url = item.getUrl();
            if (mItems.size() > 1) {
                int endPos = position - 10;
                //一共十个以内
                if (endPos <= 0) {
                    endPos = 0;
                }
                //十个以上
                /*缓存后面十个*/
                if (endPos > 0) {
                    for (int i = position - 1; i >= endPos; i--) {
                        if (i < mItems.size()) {
                            url = mItems.get(i).getUrl();
                            String id = mItems.get(i).getId();
                            if (url != null) {
                                mThumbnailThread.queueThumbnailCache(id, url);
                            }
                        }
                    }
                }
                /*缓存前十个*/
                for (int i = position + 1; i <= position + 10; i++) {
                    if (i < mItems.size()) {
                        url = mItems.get(i).getUrl();
                        String id = mItems.get(i).getId();
                        if (url != null) {
                            mThumbnailThread.queueThumbnailCache(id, url);
                        }
                    }
                }
            }
            return convertView;
        }
    }
}
