package com.vanipy.saku

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    private val vault = MockVault()
    private var showArchived = false
    private lateinit var adapter: EntryAdapter
    private lateinit var drawer: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        seed()
        drawer = DrawerLayout(this)
        val content = SwipeRefreshLayout(this)
        val list = RecyclerView(this)
        list.layoutManager = LinearLayoutManager(this)
        adapter = EntryAdapter { e ->
            startActivity(Intent(this, EntryDetailActivity::class.java)
                .putExtra("site", e.site).putExtra("username", e.username)
                .putExtra("password", e.password).putExtra("origin", e.origin.name)
                .putExtra("version", e.version)
                .putExtra("lastChange", e.lastChangeMillis)
                .putExtra("lastSync", e.lastSyncMillis ?: -1L))
        }
        list.adapter = adapter
        val bottom = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bottom.visibility = View.GONE
        content.addView(list)
        content.setOnRefreshListener {
            // MOCK: no network exists in this build. Show the thin bottom bar
            // briefly, then report the honest state.
            bottom.visibility = View.VISIBLE
            content.postDelayed({
                bottom.visibility = View.GONE
                content.isRefreshing = false
                adapter.flashSyncState("Sync unavailable in mock build (no network permission)")
            }, 600)
        }
        drawer.addView(content)
        val menu = ListView(this)
        menu.setBackgroundColor(0xFFF5F5F5.toInt())
        menu.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            arrayOf("Show archived: OFF", "Pairing status", "About"))
        menu.setOnItemClickListener { _, _, pos, _ ->
            when (pos) {
                0 -> { showArchived = !showArchived
                       (menu.adapter as ArrayAdapter<*>).notifyDataSetChanged()
                       menu.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
                           arrayOf("Show archived: " + if (showArchived) "ON" else "OFF",
                                   "Pairing status", "About"))
                       refresh() }
                1 -> startActivity(Intent(this, PairingActivity::class.java))
                2 -> adapter.flashSyncState(getString(R.string.version_text))
            }
            drawer.closeDrawer(Gravity.START)
        }
        val lp = DrawerLayout.LayoutParams((resources.displayMetrics.density * 280).toInt(),
            ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START)
        drawer.addView(menu, lp)
        setContentView(drawer)
        refresh()
        adapter.flashSyncState("MOCK - synthetic examples only. No storage or sync.")
    }

    private fun refresh() { adapter.submit(ArchiveFilter.visible(vault.all(), showArchived)) }

    private fun seed() {
        val now = 1577880000000L // Fixed synthetic demo timestamp: 2020-01-01T12:00:00Z
        vault.add(Entry("s1", "example.test", "demo-user", "SYNTHETIC-correct-horse",
            Origin.AGENT, 2, now - 3_600_000, now - 1_800_000))
        vault.add(Entry("s2", "mocksite.invalid", "demo-user", "SYNTHETIC-battery-staple",
            Origin.MANUAL, 1, now - 86_400_000, null))
        vault.add(Entry("s3", "old.example.test", "legacy", "SYNTHETIC-archived-entry",
            Origin.AGENT, 3, now - 604_800_000, now - 604_700_000, archived = true))
    }
}

class EntryAdapter(val onClick: (Entry) -> Unit) :
    RecyclerView.Adapter<EntryAdapter.VH>() {
    private var rows: List<Entry> = emptyList()
    private var notice: String? = null
    fun submit(l: List<Entry>) { rows = l; notice = null; notifyDataSetChanged() }
    fun flashSyncState(msg: String) { notice = msg; notifyDataSetChanged() }
    class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)
    override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
        val tv = TextView(p.context)
        tv.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tv.setPadding(48, 32, 48, 32); tv.textSize = 16f
        tv.setTextColor(0xFF173F3A.toInt())
        return VH(tv)
    }
    override fun getItemCount() = rows.size + if (notice != null) 1 else 0
    override fun onBindViewHolder(h: VH, pos: Int) {
        if (pos >= rows.size) { h.tv.text = notice; h.tv.setOnClickListener(null); return }
        val e = rows[pos]
        val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        h.tv.text = "${e.site}\n${e.username}  •  ${e.origin.name.lowercase()}  •  v${e.version}\n" +
            "Changed: ${df.format(Date(e.lastChangeMillis))}" +
            "\nSynced: ${e.lastSyncMillis?.let { df.format(Date(it)) } ?: "never"}" +
            if (e.archived) "  [archived]" else ""
        h.tv.setOnClickListener { onClick(e) }
    }
}
