package com.yuki.whereismycar;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps2d.AMap;
import com.amap.api.maps2d.CameraUpdateFactory;
import com.amap.api.maps2d.LocationSource;
import com.amap.api.maps2d.MapView;
import com.amap.api.maps2d.model.BitmapDescriptor;
import com.amap.api.maps2d.model.BitmapDescriptorFactory;
import com.amap.api.maps2d.model.LatLng;
import com.amap.api.maps2d.model.Marker;
import com.amap.api.maps2d.model.MarkerOptions;
import com.yuki.whereismycar.util.PermissionUtils;

public class MainActivity extends AppCompatActivity implements LocationSource, AMapLocationListener {
    private AMap aMap;
    private MapView mapView;
    private ImageView ivLocation;
    private ImageView ivShare;
    private ImageView ivFind;
    private TextView tvLocation;
    private OnLocationChangedListener mListener;
    private AMapLocationClient mlocationClient;
    private AMapLocationClientOption mLocationOption;
    private String pos;
    private AlertDialog alertDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mapView = (MapView) findViewById(R.id.map);
        mapView.onCreate(savedInstanceState);// 此方法必须重写
        ivLocation = (ImageView) findViewById(R.id.iv_location);
        ivShare = (ImageView) findViewById(R.id.iv_share);
        ivFind = (ImageView) findViewById(R.id.iv_find);
        tvLocation = (TextView) findViewById(R.id.tv_location);
        PermissionUtils.requestPermission(this, PermissionUtils.CODE_ACCESS_FINE_LOCATION, mPermissionGrant);
        PermissionUtils.requestPermission(this, PermissionUtils.CODE_ACCESS_COARSE_LOCATION, mPermissionGrant);
        init();
    }

    /**
     * 初始化
     */
    private void init() {
        if (aMap == null) {
            aMap = mapView.getMap();
            setUpMap();
        }

        View.OnClickListener clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (view.getId()) {
                    case R.id.iv_location:
                        startLocation();
                        break;
                    case R.id.iv_share:
                        if (pos != null) {
                            ClipboardManager cmb = (ClipboardManager) getApplication().getSystemService(MainActivity.CLIPBOARD_SERVICE);
                            cmb.setText(pos);
                            Toast.makeText(MainActivity.this, "成功复制位置信息，可以发给朋友们了！", Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case R.id.iv_find:
                        getSharePos();
                        break;
                }
            }
        };
        ivLocation.setOnClickListener(clickListener);
        ivShare.setOnClickListener(clickListener);
        ivFind.setOnClickListener(clickListener);
    }

    private void getSharePos() {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        dialog.setView(LayoutInflater.from(this).inflate(R.layout.alert_dialog, null));
        dialog.show();
        dialog.getWindow().setContentView(R.layout.alert_dialog);
        Button btnPositive = (Button) dialog.findViewById(R.id.bn_share_pos_commit);
        Button btnNegative = (Button) dialog.findViewById(R.id.bn_share_pos_cancel);
        final EditText etContent = (EditText) dialog.findViewById(R.id.tv_share_pos);
        btnPositive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                String str = etContent.getText().toString();
                if (isNullEmptyBlank(str)) {
                    etContent.setError("输入内如不能为空");
                } else {
                    if (getLatLng(str) == null) {
                        Toast.makeText(MainActivity.this, "位置信息无法识别", Toast.LENGTH_LONG).show();
                    } else {
                        String[] pos = getLatLng(str).split(",");
                        if (pos.length == 2) {
                            LatLng latlng = new LatLng(Double.valueOf(pos[0]), Double.valueOf(pos[1]));
                            aMap.moveCamera(CameraUpdateFactory.changeLatLng(latlng));
                            addMarker(latlng);
                        } else {
                            Toast.makeText(MainActivity.this, "位置信息无法识别", Toast.LENGTH_LONG).show();
                        }
                        dialog.dismiss();
                    }
                }
            }
        });
        btnNegative.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                dialog.dismiss();
            }
        });
    }

    private static boolean isNullEmptyBlank(String str) {
        if (str == null || "".equals(str) || "".equals(str.trim()))
            return true;
        return false;
    }

    /**
     * 设置一些amap的属性
     */
    private void setUpMap() {
        aMap.setLocationSource(this);// 设置定位监听
        aMap.getUiSettings().setMyLocationButtonEnabled(false);// 设置默认定位按钮是否显示
        aMap.setMyLocationEnabled(true);// 设置为true表示显示定位层并可触发定位，false表示隐藏定位层并不可触发定位，默认是false
    }

    /**
     * 方法必须重写
     */
    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    /**
     * 方法必须重写
     */
    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        deactivate();
    }

    /**
     * 方法必须重写
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    /**
     * 方法必须重写
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
        if(null != mlocationClient){
            mlocationClient.onDestroy();
        }
    }

    @Override
    public void onLocationChanged(AMapLocation aMapLocation) {
        if (mListener != null && aMapLocation != null) {
            if (aMapLocation != null  && aMapLocation.getErrorCode() == 0) {
                LatLng latlng = new LatLng(aMapLocation.getLatitude(), aMapLocation.getLongitude());
                aMap.moveCamera(CameraUpdateFactory.zoomTo(17));
                aMap.moveCamera(CameraUpdateFactory.changeLatLng(latlng));
                addMarker(latlng);
                String strLatlng = String.format("[%s,%s]", aMapLocation.getLatitude(), aMapLocation.getLongitude());
                pos = aMapLocation.getAddress()+strLatlng;
                tvLocation.setText(pos);
                alertDialog.dismiss();
            } else {
                String errText = "定位失败," + aMapLocation.getErrorCode()+ ": " + aMapLocation.getErrorInfo();
                Log.e("AmapErr",errText);
            }
        }
    }

    private void addMarker(LatLng latlng) {
        Bitmap bMap = BitmapFactory.decodeResource(this.getResources(),
                R.drawable.car);
        BitmapDescriptor des = BitmapDescriptorFactory.fromBitmap(bMap);

//		BitmapDescriptor des = BitmapDescriptorFactory.fromResource(R.drawable.navi_map_gps_locked);
        MarkerOptions options = new MarkerOptions();
        options.icon(des);
        options.anchor(0.5f, 0.5f);
        options.position(latlng);
        aMap.clear();
        aMap.addMarker(options);
    }

    @Override
    public void activate(OnLocationChangedListener onLocationChangedListener) {
        mListener = onLocationChangedListener;
        startLocation();
    }

    @Override
    public void deactivate() {

    }

    private void startLocation() {
        if (mlocationClient == null) {
            mlocationClient = new AMapLocationClient(this);
            mLocationOption = new AMapLocationClientOption();
            //设置定位监听
            mlocationClient.setLocationListener(this);
            //设置为高精度定位模式
            mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
            mLocationOption.setOnceLocation(true);
            //设置定位参数
            mlocationClient.setLocationOption(mLocationOption);
            // 此方法为每隔固定时间会发起一次定位请求，为了减少电量消耗或网络流量消耗，
            // 注意设置合适的定位时间的间隔（最小间隔支持为2000ms），并且在合适时间调用stopLocation()方法来取消定位请求
            // 在定位结束后，在合适的生命周期调用onDestroy()方法
            // 在单次定位情况下，定位无论成功与否，都无需调用stopLocation()方法移除请求，定位sdk内部会移除
            mlocationClient.startLocation();
        } else {
            mlocationClient.startLocation();
        }
        alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.show();
        alertDialog.getWindow().setContentView(R.layout.locating);
    }

    private String getLatLng(String str) {
        if (str.contains("[") && str.contains("]")) {
            String latlng=str.substring(str.lastIndexOf("["),str.lastIndexOf("]"));
            String res=latlng.substring(1,latlng.length());
            Log.e("yuki", res);
            return res;
        } else {
            return null;
        }
    }

    private PermissionUtils.PermissionGrant mPermissionGrant = new PermissionUtils.PermissionGrant() {
        @Override
        public void onPermissionGranted(int requestCode) {
            switch (requestCode) {
                case PermissionUtils.CODE_RECORD_AUDIO:
                    //Toast.makeText(MainActivity.this, "Result Permission Grant CODE_RECORD_AUDIO", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_GET_ACCOUNTS:
                    //Toast.makeText(MainActivity.this, "Result Permission Grant CODE_GET_ACCOUNTS", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_READ_PHONE_STATE:
                    //Toast.makeText(MainActivity.this, "Result Permission Grant CODE_READ_PHONE_STATE", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_CALL_PHONE:
                    //Toast.makeText(MainActivity.this, "Result Permission Grant CODE_CALL_PHONE", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_CAMERA:
                    //Toast.makeText(MainActivity.this, "Result Permission Grant CODE_CAMERA", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_ACCESS_FINE_LOCATION:
                    //Toast.makeText(MainActivity.this, "Result Permission Grant CODE_ACCESS_FINE_LOCATION", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_ACCESS_COARSE_LOCATION:
                    //Toast.makeText(MainActivity.this, "Result Permission Grant CODE_ACCESS_COARSE_LOCATION", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_READ_EXTERNAL_STORAGE:
                    //Toast.makeText(MainActivity.this, "Result Permission Grant CODE_READ_EXTERNAL_STORAGE", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_WRITE_EXTERNAL_STORAGE:
                    //Toast.makeText(MainActivity.this, "Result Permission Grant CODE_WRITE_EXTERNAL_STORAGE", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    break;
            }
        }
    };

    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        PermissionUtils.requestPermissionsResult(this, requestCode, permissions, grantResults, mPermissionGrant);
    }
}
