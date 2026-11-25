package app.zen.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.systemGestureExclusion
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zen.android.browser.UrlInput
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

@Composable
fun BrowserScreen(vm: BrowserViewModel) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val spaces by vm.spaces.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()

    LaunchedEffect(drawerState.isOpen) { if (drawerState.isOpen) vm.syncNow() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Page content owns horizontal gestures; the drawer only opens from the edge strip below.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            SpacesDrawer(
                state = spaces,
                status = status,
                activeSpace = vm.activeSpace,
                activeTab = vm.activeTab,
                onSpaceChange = { vm.activeSpace = it },
                onTab = { tab ->
                    vm.selectTab(tab.id, tab.url)
                    scope.launch { drawerState.close() }
                },
                onCloseTab = vm::closeTab,
                onNewTab = {
                    vm.newTab()
                    scope.launch { drawerState.close() }
                },
                onSignIn = {
                    vm.beginLogin()
                    scope.launch { drawerState.close() }
                },
                onSignOut = { vm.signOut() },
                onSync = { vm.syncNow() },
            )
        },
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            GeckoContent(vm.session, Modifier.fillMaxSize().statusBarsPadding())

            EdgeSwipe(Modifier.align(Alignment.CenterStart)) { scope.launch { drawerState.open() } }

            AnimatedVisibility(
                visible = vm.chromeVisible,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                AddressPill(
                    url = vm.url,
                    progress = vm.progress,
                    onMenu = { scope.launch { drawerState.open() } },
                    onSubmit = vm::navigate,
                    onNewTab = { vm.newTab() },
                )
            }

            vm.loginSession?.let { login ->
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        GeckoContent(login, Modifier.fillMaxSize().padding(top = 48.dp))
                        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = vm::cancelLogin) { Icon(Icons.Default.Close, "Cancel sign-in") }
                            Text("Sign in to sync with Zen", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }

            vm.error?.let { message ->
                Snackbar(
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp),
                    action = { Text("OK", Modifier.padding(8.dp)) },
                ) { Text(message) }
                LaunchedEffect(message) {
                    kotlinx.coroutines.delay(5000)
                    vm.error = null
                }
            }
        }
    }
}

@Composable
private fun GeckoContent(session: GeckoSession?, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { GeckoView(it) },
        update = { view ->
            if (session != null && view.session !== session) {
                view.releaseSession()
                view.setSession(session)
            }
        },
    )
}

/** Invisible strip on the left edge, excluded from Android's back gesture, that pulls the sidebar open. */
@Composable
private fun EdgeSwipe(modifier: Modifier, onOpen: () -> Unit) {
    var dragged by remember { mutableStateOf(0f) }
    Box(
        modifier
            .width(20.dp)
            .fillMaxHeight(0.6f)
            .systemGestureExclusion()
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { dragged += it },
                onDragStarted = { dragged = 0f },
                onDragStopped = { velocity -> if (dragged > 40f || velocity > 600f) onOpen() },
            ),
    )
}

@Composable
private fun AddressPill(
    url: String,
    progress: Int,
    onMenu: () -> Unit,
    onSubmit: (String) -> Unit,
    onNewTab: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    Surface(
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth(),
    ) {
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "Spaces") }
                if (editing) {
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            onSubmit(text)
                            editing = false
                            focusManager.clearFocus()
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                    LaunchedEffect(Unit) { focus.requestFocus() }
                } else {
                    Text(
                        text = UrlInput.display(url).ifEmpty { "Search or enter address" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 14.dp)
                            .then(Modifier.clickableNoRipple {
                                text = url
                                editing = true
                            }),
                    )
                }
                IconButton(onClick = if (editing) ({ editing = false; focusManager.clearFocus() }) else onNewTab) {
                    Icon(if (editing) Icons.Default.Close else Icons.Default.Add, if (editing) "Cancel" else "New tab")
                }
            }
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomCenter),
                )
            }
        }
    }
}
