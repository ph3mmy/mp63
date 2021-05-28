package ng.com.plustech.mp63kotlin.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.TextView
import ng.com.plustech.mp63kotlin.R
import ng.com.plustech.mp63kotlin.models.Device


class BluetoothListAdapter(private val context: Context, private val clickListener: (Device)->Unit): BaseAdapter() {
    private var devices: ArrayList<Device> = java.util.ArrayList<Device>()
    private val inflater: LayoutInflater
            = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    override fun getCount(): Int {
        return devices.size
    }

    override fun getItem(position: Int): Any {
        return devices[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var viewHolder: ViewHolder? = null
        val device: Device = devices[position]

        val rowView = inflater.inflate(R.layout.bluetoothlist_item, parent, false)

        val checkBox = rowView.findViewById(R.id.checkBox) as CheckBox
        val name = rowView.findViewById(R.id.name) as TextView


        viewHolder = ViewHolder(checkBox, name)
        viewHolder.clickedCheck.isClickable = device.selected
        viewHolder.clickedCheck.text = device.name

        viewHolder.clickedCheck.setOnClickListener {

            device.selected = !device.selected

            clickListener(device)
        }

        return rowView
    }

    fun addItem(item: Device) {
        for (dItem: Device in devices) {
            if(dItem.address == item.address) {
                //the device already exist in our list
                if(item.name.isNotEmpty()) {
                    //set new name for the device
                    dItem.name = item.name
                    dItem.selected = item.selected
                }
                return
            }
        }
        devices.add(item)
    }

    internal class ViewHolder(var clickedCheck: CheckBox, msg: TextView) {
        protected var msg: TextView

        init {
            clickedCheck.isClickable = false
            clickedCheck.isFocusable = false
            this.msg = msg
        }
    }
}