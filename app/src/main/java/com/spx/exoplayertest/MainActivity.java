package com.spx.exoplayertest;

import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.sogo.exoplayer.LdExoPlayerController;

import java.util.Arrays;
import java.util.List;

/**
 *
 */
public class MainActivity extends AppCompatActivity {
    private String[] sources = new String[]{
            "http://video.wenwen.sogou.com/e75aa445e10f4a51cf9b1df72b947e40.mp4.f20.mp4",
            "http://video.wenwen.sogou.com/8640cc39cd2abaa644dbc9f4eef4b49e.mp4.f20.mp4",
            "http://video.wenwen.sogou.com/31bc728f5bd22e0cf6aa4898f7f78f0d.mp4.f20.mp4",
            "http://video.wenwen.sogou.com/8cd0ffa1bea136eef0b42057f305b7b0.mp4.f20.mp4",
            "http://video.wenwen.sogou.com/4dfd051e27a32ae319588e7066937398.mp4.f20.mp4",
    };

    private RecyclerView mRecyclerView;
    private Adapter mAdapter;

    private List<String> mUrls = Arrays.asList(sources);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mRecyclerView = findViewById(R.id.recyclerView);
        mAdapter = new Adapter();
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setAdapter(mAdapter);


    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private class ViewHolder extends RecyclerView.ViewHolder {

        private LdExoPlayerController controller;
        private TextView titleTv;
        private View playView;

        public ViewHolder(final View itemView) {
            super(itemView);
            controller = new LdExoPlayerController(itemView);
            titleTv = itemView.findViewById(R.id.title_tv);
            playView = itemView.findViewById(R.id.player_view);


            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int adapterPosition = getAdapterPosition();
                    String url = mUrls.get(adapterPosition);
                    Intent intent = new Intent(itemView.getContext(), VideoPlayActivity.class);
                    intent.putExtra("play_url", url);
                    intent.putExtra("play_id", adapterPosition);
                    int[] location = new int[2];
                    playView.getLocationOnScreen(location);
                    int width = playView.getWidth();
                    int height = playView.getHeight();
                    intent.putExtra("view_x", location[0]);
                    intent.putExtra("view_y", location[1]);
                    intent.putExtra("view_w", width);
                    intent.putExtra("view_h", height);

                    itemView.getContext().startActivity(intent);
                    itemView.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            controller.onDetached();
                        }
                    }, 300);
                }
            });
        }

        public void bindData(int position) {
            titleTv.setText("这是测试数据:[" + position + "] url:" + mUrls.get(position));

            controller.bindData(itemView.getContext(), position,
                    "position_" + position, mUrls.get(position), "");
        }
    }

    private class Adapter extends RecyclerView.Adapter<ViewHolder> {

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View view = inflater.inflate(R.layout.list_item_layout, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.bindData(position);
        }

        @Override
        public int getItemCount() {
            return mUrls.size();
        }
    }
}
