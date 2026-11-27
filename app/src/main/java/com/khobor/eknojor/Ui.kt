@file:OptIn(ExperimentalMaterial3Api::class)

package com.khobor.eknojor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun MainScreen(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("খবর এক নজরে") },
                actions = {
                    if (vm.refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(16.dp))
                    } else {
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "রিফ্রেশ")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavItem(tab == 0, { tab = 0 }, Icons.Default.Home, "বাংলাদেশ")
                NavItem(tab == 1, { tab = 1 }, Icons.Default.Place, "বিশ্ব")
                NavItem(tab == 2, { tab = 2 }, Icons.Default.CheckCircle, "ফ্যাক্ট চেক")
                NavItem(tab == 3, { tab = 3 }, Icons.Default.Star, "সেভ")
                NavItem(tab == 4, { tab = 4 }, Icons.Default.Settings, "সেটিংস")
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> StoryList(vm, vm.bdStories)
                1 -> StoryList(vm, vm.worldStories)
                2 -> FactCheckTab(vm)
                3 -> SavedTab(vm)
                else -> SettingsTab(vm)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
    )
}

// ================================================================================================
// News lists
// ================================================================================================

@Composable
fun StoryList(vm: MainViewModel, stories: List<Story>) {
    val shown = remember(stories, vm.sortByCoverage) {
        if (vm.sortByCoverage) {
            stories.sortedWith(
                compareByDescending<Story> { minOf(it.sourceCount, 5) }.thenByDescending { it.latest }
            )
        } else stories
    }
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = vm.sortByCoverage,
                    onClick = { vm.sortByCoverage = !vm.sortByCoverage },
                    label = { Text("বেশি সূত্রের খবর আগে") }
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "আপডেট: " + timeAgo(vm.lastRefresh),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (shown.isEmpty()) {
            item { EmptyState(vm) }
        }
        items(shown, key = { it.id }) { story -> StoryCard(story, vm) }
    }
}

@Composable
fun EmptyState(vm: MainViewModel) {
    val text = when {
        vm.refreshing || !vm.loaded -> "খবর আনা হচ্ছে..."
        else -> "কোনো খবর নেই। ইন্টারনেট চালু করে উপরের রিফ্রেশ বোতাম চাপুন।"
    }
    Text(text, modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun StoryCard(story: Story, vm: MainViewModel) {
    val ctx = LocalContext.current
    var expanded by rememberSaveable(story.id) { mutableStateOf(false) }
    val head = story.headline

    Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TierLabel(story.tier)
            Text(
                head.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = if (expanded) 8 else 3,
                overflow = TextOverflow.Ellipsis
            )
            if (head.summary.isNotBlank()) {
                Text(
                    head.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) 8 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "${bn(story.sourceCount)}টি সূত্র • ${timeAgo(story.latest)}",
                style = MaterialTheme.typography.labelMedium
            )
            if (expanded) {
                Text("কোন কোন সূত্র লিখেছে (চাপলে মূল খবর খুলবে):", style = MaterialTheme.typography.labelMedium)
                for (a in story.articles) {
                    Column(
                        Modifier.fillMaxWidth().clickable { openLink(ctx, a.link) }.padding(vertical = 4.dp)
                    ) {
                        Text(a.sourceName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            a.title,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                ActionRow(head, vm)
            }
        }
    }
}

@Composable
fun ActionRow(a: Article, vm: MainViewModel) {
    val ctx = LocalContext.current
    val isSaved = a.id in vm.savedIds
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { vm.toggleSaved(a) }) {
            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (isSaved) "সেভ করা আছে" else "সেভ করুন")
        }
        TextButton(onClick = { shareArticle(ctx, a) }) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("শেয়ার")
        }
    }
}

@Composable
fun TierLabel(tier: Tier) {
    val (text, color) = when (tier) {
        Tier.CONFIRMED -> "বহু সূত্রে নিশ্চিত (৩+ সূত্র)" to MaterialTheme.colorScheme.primary
        Tier.TWO -> "২টি সূত্রে এসেছে" to MaterialTheme.colorScheme.tertiary
        Tier.SINGLE -> "একক সূত্র • এখনো অন্য সূত্রে আসেনি" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
}

@Composable
fun ArticleCard(a: Article, vm: MainViewModel) {
    val ctx = LocalContext.current
    Card(Modifier.fillMaxWidth().clickable { openLink(ctx, a.link) }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                a.sourceName + " • " + timeAgo(a.publishedAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(a.title, style = MaterialTheme.typography.titleMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (a.summary.isNotBlank()) {
                Text(
                    a.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ActionRow(a, vm)
        }
    }
}

@Composable
fun FactCheckTab(vm: MainViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "ফ্যাক্ট-চেক সংস্থাগুলোর সাম্প্রতিক প্রতিবেদন। কোনো গুজব বা সন্দেহজনক খবর দেখলে এখানে মিলিয়ে নিন।",
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (vm.factChecks.isEmpty()) item { EmptyState(vm) }
        items(vm.factChecks, key = { it.id }) { a -> ArticleCard(a, vm) }
    }
}

@Composable
fun SavedTab(vm: MainViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (vm.saved.isEmpty()) {
            item {
                Text(
                    "এখনো কিছু সেভ করা হয়নি। কোনো খবর খুলে \"সেভ করুন\" চাপুন।",
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
        items(vm.saved, key = { it.id }) { a -> ArticleCard(a, vm) }
    }
}

// ================================================================================================
// Settings
// ================================================================================================

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium)

@Composable
fun SettingsTab(vm: MainViewModel) {
    val ctx = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setNotify(granted)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard("বড় খবরের নোটিফিকেশন") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "অন্তত ৪টি সূত্রে একই খবর এলে জানান",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = vm.notifyEnabled,
                    onCheckedChange = { on ->
                        val need = on && Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        if (need) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.setNotify(on)
                    }
                )
            }
        }

        SectionCard("কত পর পর নতুন খবর আনবে") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in listOf(30, 60, 180)) {
                    FilterChip(
                        selected = vm.intervalMinutes == m,
                        onClick = { vm.setInterval(m) },
                        label = { Text(if (m < 60) "${bn(m)} মিনিট" else "${bn(m / 60)} ঘণ্টা") }
                    )
                }
            }
            Body("অ্যাপ খোলা থাকলে বা খুললে আলাদাভাবে নতুন খবর আনে। ব্যাকগ্রাউন্ডে শুধু ইন্টারনেট থাকলে চলে।")
        }

        SectionCard("সূত্রের অবস্থা") {
            Body("শেষ আপডেট: " + timeAgo(vm.lastRefresh))
            for (s in vm.sources) {
                val st = vm.statuses[s.id]
                val (text, bad) = when {
                    st == null -> "এখনো আনা হয়নি" to false
                    st.ok -> "চালু • ${bn(st.count)}টি খবর • " + timeAgo(st.time) to false
                    else -> "ব্যর্থ: " + st.error to true
                }
                Column {
                    Text(s.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (bad) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        SectionCard("এই অ্যাপ কীভাবে কাজ করে") {
            Body(
                "অ্যাপ বিভিন্ন পত্রিকা ও সংবাদমাধ্যমের প্রকাশ্য RSS ফিড থেকে শুধু শিরোনাম, ছোট সারাংশ ও লিংক আনে। " +
                    "সম্পূর্ণ খবর পড়তে চাপলে মূল সাইট খোলে। খবরের লেখা এই অ্যাপে কপি করা হয় না।"
            )
            Body(
                "একই ঘটনার খবর একসাথে এক কার্ডে দেখানো হয়। কার্ডের চিহ্ন বলে কতগুলো আলাদা সূত্র খবরটি দিয়েছে: " +
                    "৩+ সূত্র হলে \"বহু সূত্রে নিশ্চিত\", ২টি হলে \"২টি সূত্রে\", একটি হলে \"একক সূত্র\"।"
            )
            Body(
                "গুরুত্বপূর্ণ: এই চিহ্ন খবর কতজন প্রকাশ করেছে তা দেখায়, খবর যে সত্য তার প্রমাণ নয়। " +
                    "একাধিক সংস্থা একই ভুল উৎস থেকেও খবর নিতে পারে। সন্দেহ হলে \"ফ্যাক্ট চেক\" ট্যাব দেখুন। " +
                    "শিরোনাম মিলিয়ে খবর গোছানো হয়, তাই কখনো ভুল গোছানোও হতে পারে।"
            )
            Body("গোপনীয়তা: এই সংস্করণে অ্যাপ আপনার কোনো ব্যক্তিগত তথ্য সংগ্রহ বা পাঠায় না। সেভ করা খবর শুধু আপনার ফোনে থাকে।")
        }
    }
}
