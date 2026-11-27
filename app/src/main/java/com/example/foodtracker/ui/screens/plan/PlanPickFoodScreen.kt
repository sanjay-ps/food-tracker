package com.example.foodtracker.ui.screens.plan

import com.example.foodtracker.ui.components.atoms.AppTextField
import com.example.foodtracker.ui.components.atoms.cardColor
import androidx.compose.ui.graphics.Color
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.X
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.foodtracker.data.PlanSlot
import com.example.foodtracker.data.db.FoodEntity
import com.example.foodtracker.ui.appViewModel
import com.example.foodtracker.ui.components.EmptyState
import com.example.foodtracker.ui.components.FoodRow
import com.example.foodtracker.ui.components.atoms.AppDivider
import com.example.foodtracker.ui.components.atoms.AppTextButton
import com.example.foodtracker.ui.components.atoms.AppTopBar
import com.example.foodtracker.ui.rememberAppHaptics
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Spacing

/**
 * Pick a food to plan for one slot — a meal, or no particular time.
 *
 * **Local foods only, and no tabs.** The Log picker needs Often / Favourites /
 * Custom because it is used several times a day against 160-odd foods and the
 * right one is usually in your recent history. Planning happens once: you
 * already know what you are looking for, so a search field over the whole
 * catalogue is the shorter path, and four tabs would be four controls standing
 * between the user and a text box they were always going to use.
 *
 * The one discovery route it does offer is Rich-in, because that is the
 * decision this screen exists to support — *what should I be eating more of* —
 * and it arrives there carrying the slot, so a result can be added without
 * coming back here first.
 */
@Composable
fun PlanPickFoodScreen(
    slot: PlanSlot,
    onBack: () -> Unit,
    onOpenRichFoods: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: PlanAddViewModel = appViewModel { PlanAddViewModel(it.repository) }
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val haptics = rememberAppHaptics()

    var picked by remember { mutableStateOf<FoodEntity?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = "Add to ${slot.displayName.lowercase()}",
                onBack = onBack,
                actions = {
                    AppTextButton(text = "Rich in…", onClick = { haptics.light(); onOpenRichFoods() })
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AppTextField(
                value = query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Search ${results.size} foods…", style = AppText.body) },
                leadingIcon = { Icon(Lucide.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) {
                            Icon(Lucide.X, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.gutter)
                    .padding(bottom = Spacing.m),
                shape = RoundedCornerShape(Radius.pill),
                singleLine = true,
            )

            // Only once there is a query to have failed. The results flow
            // starts empty and fills a frame later, so testing emptiness alone
            // flashes "No foods match" — against a blank query, which reads as
            // the app claiming it has no foods at all.
            if (results.isEmpty() && query.isNotBlank()) {
                EmptyState(
                    icon = Lucide.SlidersHorizontal,
                    title = "No foods match",
                    message = "Nothing on this phone matches \"$query\". Add it as a " +
                        "custom food from the Log screen first, and it will be " +
                        "available to plan.",
                    modifier = Modifier.padding(horizontal = Spacing.gutter),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(results, key = { _, food -> food.id }) { index, food ->
                        if (index > 0) AppDivider()
                        FoodRow(food = food, onClick = { haptics.light(); picked = food })
                    }
                }
            }
        }
    }

    picked?.let { food ->
        PlanAmountSheet(
            food = food,
            slot = slot,
            // Back to the editor on confirm. Planning a food is a one-shot
            // errand — you came here to add idli, not to browse — and leaving
            // the picker open would make the next tap add a second food to a
            // plan the user thinks they have finished editing.
            onConfirm = { item -> vm.add(item, onDone = onBack) },
            onDismiss = { picked = null },
        )
    }
}
