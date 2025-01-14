package com.faforever.client.game;

import com.faforever.client.domain.api.FeaturedMod;
import com.faforever.client.domain.api.Map;
import com.faforever.client.domain.api.MapVersion;
import com.faforever.client.domain.api.ModVersion;
import com.faforever.client.featuredmod.FeaturedModService;
import com.faforever.client.filter.MapFilterController;
import com.faforever.client.fx.contextmenu.ContextMenuBuilder;
import com.faforever.client.i18n.I18n;
import com.faforever.client.map.MapService;
import com.faforever.client.map.generator.MapGeneratorService;
import com.faforever.client.mod.ModManagerController;
import com.faforever.client.mod.ModService;
import com.faforever.client.net.ConnectionState;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.preferences.LastGamePrefs;
import com.faforever.client.test.PlatformTest;
import com.faforever.client.theme.UiService;
import com.faforever.client.ui.dialog.Dialog;
import com.faforever.client.user.LoginService;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.scene.control.SplitPane;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static org.instancio.Select.field;
import static org.instancio.Select.scope;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CreateGameControllerTest extends PlatformTest {
  private static final KeyEvent keyUpPressed = new KeyEvent(KeyEvent.KEY_PRESSED, "0", "", KeyCode.UP, false, false, false, false);
  private static final KeyEvent keyUpReleased = new KeyEvent(KeyEvent.KEY_RELEASED, "0", "", KeyCode.UP, false, false, false, false);
  private static final KeyEvent keyDownPressed = new KeyEvent(KeyEvent.KEY_PRESSED, "0", "", KeyCode.DOWN, false, false, false, false);
  private static final KeyEvent keyDownReleased = new KeyEvent(KeyEvent.KEY_RELEASED, "0", "", KeyCode.DOWN, false, false, false, false);

  @Mock
  private GameRunner gameRunner;
  @Mock
  private MapService mapService;
  @Mock
  private ModService modService;
  @Mock
  private FeaturedModService featuredModService;
  @Mock
  private NotificationService notificationService;
  @Mock
  private I18n i18n;
  @Mock
  private UiService uiService;
  @Mock
  private ContextMenuBuilder contextMenuBuilder;
  @Mock
  private LoginService loginService;
  @Mock
  private MapGeneratorService mapGeneratorService;
  @Mock
  private ModManagerController modManagerController;
  @Mock
  private GenerateMapController generateMapController;
  @Mock
  private MapFilterController mapFilterController;
  @Spy
  private LastGamePrefs lastGamePrefs;

  private CreateGameController instance;
  private ObservableList<MapVersion> mapList;

  @BeforeEach
  public void setUp() throws Exception {
    instance = new CreateGameController(gameRunner, mapService, featuredModService, modService, i18n,
                                        notificationService, loginService, mapGeneratorService, uiService,
                                        contextMenuBuilder, lastGamePrefs, fxApplicationThreadExecutor);

    mapList = FXCollections.observableArrayList();

    lenient().when(featuredModService.getFeaturedMods())
             .thenReturn(Flux.just(Instancio.of(FeaturedMod.class)
                                            .set(field(FeaturedMod::technicalName), "faf")
                                            .set(field(FeaturedMod::visible), true)
                                            .create()));

    lenient().when(mapGeneratorService.downloadGeneratorIfNecessary(any())).thenReturn(Mono.empty());
    lenient().when(mapGeneratorService.getGeneratorStyles()).thenReturn(Mono.just(List.of()));
    lenient().when(uiService.showInDialog(any(), any(), any())).thenReturn(new Dialog());
    lenient().when(uiService.loadFxml("theme/play/generate_map.fxml")).thenReturn(generateMapController);
    lenient().when(mapService.getInstalledMaps()).thenReturn(mapList);
    lenient().when(mapService.loadPreview(anyString(), any()))
             .thenReturn(new Image("/theme/images/default_achievement.png"));
    lenient().when(i18n.get(any(), any())).then(invocation -> invocation.getArgument(0));
    lenient().when(i18n.get(any())).then(invocation -> invocation.getArgument(0));
    lenient().when(i18n.number(anyInt())).then(invocation -> invocation.getArgument(0).toString());
    lenient().when(loginService.connectionStateProperty())
             .thenReturn(new SimpleObjectProperty<>(ConnectionState.CONNECTED));
    lenient().when(loginService.getConnectionState()).thenReturn(ConnectionState.CONNECTED);
    lenient().when(modService.updateAndActivateModVersions(any()))
             .thenAnswer(invocation -> Mono.just(List.copyOf(invocation.getArgument(0))));
    lenient().when(uiService.loadFxml("theme/filter/filter.fxml", MapFilterController.class))
             .thenReturn(mapFilterController);
    lenient().when(mapFilterController.filterActiveProperty()).thenReturn(new SimpleBooleanProperty());
    lenient().when(mapFilterController.predicateProperty()).thenReturn(new SimpleObjectProperty<>(item -> true));
    lenient().when(mapFilterController.getRoot()).thenReturn(new SplitPane());

    loadFxml("theme/play/create_game.fxml", clazz -> {
      if (clazz.equals(ModManagerController.class)) {
        return modManagerController;
      }
      return instance;
    });
  }

  @Test
  public void showOnlyToFriendsRemembered() {
    instance.onlyForFriendsCheckBox.setSelected(true);
    assertThat(lastGamePrefs.isLastGameOnlyFriends(), is(true));
    instance.onlyForFriendsCheckBox.setSelected(false);
    assertThat(lastGamePrefs.isLastGameOnlyFriends(), is(false));
  }

  @Test
  public void testMapSearchTextFieldKeyPressedUpForEmpty() {
    instance.mapSearchTextField.setText("Test");

    instance.mapSearchTextField.getOnKeyPressed().handle(keyUpPressed);
    instance.mapSearchTextField.getOnKeyPressed().handle(keyUpReleased);

    assertThat(instance.mapListView.getSelectionModel().getSelectedIndex(), is(-1));
  }

  @Test
  public void testMapSearchTextFieldKeyPressedDownForEmpty() {
    instance.mapSearchTextField.setText("Test");

    instance.mapSearchTextField.getOnKeyPressed().handle(keyDownPressed);
    instance.mapSearchTextField.getOnKeyPressed().handle(keyDownReleased);

    assertThat(instance.mapListView.getSelectionModel().getSelectedIndex(), is(-1));
  }

  @Test
  public void testSetLastGameTitle() {
    lastGamePrefs.setLastGameTitle("testGame");

    runOnFxThreadAndWait(() -> reinitialize(instance));

    assertThat(instance.titleTextField.getText(), is("testGame"));
  }

  @Test
  public void testButtonBindingIfTitleNotSet() {
    String message = "title missing";
    when(i18n.get("game.create.titleMissing")).thenReturn(message);
    runOnFxThreadAndWait(() -> reinitialize(instance));
    assertThat(instance.titleTextField.getText(), is(""));
    assertThat(instance.createGameButton.getText(), is(message));

    runOnFxThreadAndWait(() -> instance.titleTextField.setText(" "));
    assertThat(instance.titleTextField.getText(), is(" "));
    assertThat(instance.createGameButton.getText(), is(message));
  }

  @Test
  public void testButtonBindingIfTitleNotAscii() {
    when(i18n.get("game.create.titleNotAscii")).thenReturn("title not ascii");
    runOnFxThreadAndWait(() -> {
      instance.titleTextField.setText("ты");
      reinitialize(instance);
    });

    assertThat(instance.titleTextField.getText(), is("ты"));
    assertThat(instance.createGameButton.getText(), is("title not ascii"));
  }

  @Test
  public void testButtonBindingIfPasswordNotAscii() {
    when(i18n.get("game.create.passwordNotAscii")).thenReturn("password not ascii");
    runOnFxThreadAndWait(() -> {
      instance.titleTextField.setText("Test");
      instance.passwordTextField.setText("ты");
      reinitialize(instance);
    });

    assertThat(instance.passwordTextField.getText(), is("ты"));
    assertThat(instance.createGameButton.getText(), is("password not ascii"));
  }

  @Test
  public void testButtonBindingIfNotConnected() {
    when(loginService.connectionStateProperty()).thenReturn(new SimpleObjectProperty<>(ConnectionState.DISCONNECTED));
    when(loginService.getConnectionState()).thenReturn(ConnectionState.DISCONNECTED);
    when(i18n.get("game.create.disconnected")).thenReturn("disconnected");
    runOnFxThreadAndWait(() -> reinitialize(instance));

    assertThat(instance.titleTextField.getText(), is(""));
    assertThat(instance.createGameButton.getText(), is("disconnected"));
  }

  @Test
  public void testButtonBindingIfNotConnecting() {
    when(loginService.connectionStateProperty()).thenReturn(new SimpleObjectProperty<>(ConnectionState.CONNECTING));
    when(loginService.getConnectionState()).thenReturn(ConnectionState.CONNECTING);
    when(i18n.get("game.create.connecting")).thenReturn("connecting");
    runOnFxThreadAndWait(() -> reinitialize(instance));

    assertThat(instance.titleTextField.getText(), is(""));
    assertThat(instance.createGameButton.getText(), is("connecting"));
  }

  @Test
  public void testCloseButtonTriggeredAfterCreatingGame() {
    Runnable closeAction = mock(Runnable.class);
    instance.setOnCloseButtonClickedListener(closeAction);

    MapVersion latestVersion = Instancio.create(MapVersion.class);
    when(mapService.updateLatestVersionIfNecessary(latestVersion)).thenReturn(Mono.just(latestVersion));

    mapList.add(latestVersion);
    instance.mapListView.getSelectionModel().select(latestVersion);
    instance.onCreateButtonClicked();

    verify(closeAction).run();
  }

  @Test
  public void testCreateGameWithSelectedModAndMap() {
    ArgumentCaptor<NewGameInfo> newGameInfoArgumentCaptor = ArgumentCaptor.forClass(NewGameInfo.class);
    ModVersion modVersion = Instancio.create(ModVersion.class);

    when(modManagerController.getSelectedModVersions()).thenReturn(Set.of(modVersion));

    MapVersion map = Instancio.create(MapVersion.class);
    when(mapService.updateLatestVersionIfNecessary(map)).thenReturn(Mono.just(map));

    runOnFxThreadAndWait(() -> {
      mapList.add(map);
      instance.mapListView.getSelectionModel().select(map);
      instance.setOnCloseButtonClickedListener(mock(Runnable.class));
      instance.onCreateButtonClicked();
    });

    verify(modManagerController).getSelectedModVersions();
    verify(gameRunner).host(newGameInfoArgumentCaptor.capture());
    NewGameInfo gameInfo = newGameInfoArgumentCaptor.getValue();
    assertThat(gameInfo.simMods(), contains(modVersion.uid()));
    assertThat(gameInfo.map(), is(map.folderName()));
  }

  @Test
  public void testCreateGameWithOutdatedMod() {
    ArgumentCaptor<NewGameInfo> newGameInfoArgumentCaptor = ArgumentCaptor.forClass(NewGameInfo.class);
    ModVersion modVersion = Instancio.create(ModVersion.class);

    ModVersion newModVersion = Instancio.create(ModVersion.class);

    Set<ModVersion> selectedMods = Set.of(modVersion);
    when(modManagerController.getSelectedModVersions()).thenReturn(selectedMods);

    MapVersion map = Instancio.create(MapVersion.class);
    when(mapService.updateLatestVersionIfNecessary(map)).thenReturn(Mono.just(map));

    when(modService.updateAndActivateModVersions(selectedMods)).thenReturn(Mono.just(List.of(newModVersion)));

    runOnFxThreadAndWait(() -> {
      mapList.add(map);
      instance.mapListView.getSelectionModel().select(map);
      instance.setOnCloseButtonClickedListener(mock(Runnable.class));
    });

    instance.onCreateButtonClicked();

    verify(modManagerController).getSelectedModVersions();
    verify(gameRunner).host(newGameInfoArgumentCaptor.capture());
    assertThat(newGameInfoArgumentCaptor.getValue().simMods(), contains(newModVersion.uid()));
    assertThat(newGameInfoArgumentCaptor.getValue().map(), is(map.folderName()));
  }

  @Test
  public void testCreateGameOnSelectedMapIfNoNewVersionMap() {
    ArgumentCaptor<NewGameInfo> captor = ArgumentCaptor.forClass(NewGameInfo.class);
    MapVersion map = Instancio.create(MapVersion.class);

    when(mapService.updateLatestVersionIfNecessary(map)).thenReturn(Mono.just(map));

    runOnFxThreadAndWait(() -> {
      mapList.add(map);
      instance.mapListView.getSelectionModel().select(map);
      instance.setOnCloseButtonClickedListener(mock(Runnable.class));
      instance.onCreateButtonClicked();
    });

    verify(gameRunner).host(captor.capture());
    assertThat(captor.getValue().map(), is(map.folderName()));
  }

  @Test
  public void testCreateGameOnUpdatedMapIfNewVersionMapExist() {
    ArgumentCaptor<NewGameInfo> captor = ArgumentCaptor.forClass(NewGameInfo.class);

    MapVersion outdatedMap = Instancio.of(MapVersion.class).set(field(MapVersion::folderName), "test.v0001").create();
    MapVersion updatedMap = Instancio.of(MapVersion.class).set(field(MapVersion::folderName), "test.v0002").create();
    when(mapService.updateLatestVersionIfNecessary(outdatedMap)).thenReturn(Mono.just(updatedMap));

    runOnFxThreadAndWait(() -> {
      mapList.add(outdatedMap);
      instance.mapListView.getSelectionModel().select(outdatedMap);
      instance.setOnCloseButtonClickedListener(mock(Runnable.class));
      instance.onCreateButtonClicked();
    });

    verify(gameRunner).host(captor.capture());
    assertThat(captor.getValue().map(), is(updatedMap.folderName()));
  }

  @Test
  public void testCreateGameOnSelectedMapImmediatelyIfThrowExceptionWhenUpdatingMap() {
    ArgumentCaptor<NewGameInfo> captor = ArgumentCaptor.forClass(NewGameInfo.class);

    MapVersion map = Instancio.create(MapVersion.class);
    when(mapService.updateLatestVersionIfNecessary(any())).thenReturn(
        Mono.error(new RuntimeException("error when checking for update or updating map")));

    runOnFxThreadAndWait(() -> {
      mapList.add(map);
      instance.mapListView.getSelectionModel().select(map);
      instance.setOnCloseButtonClickedListener(mock(Runnable.class));
      instance.onCreateButtonClicked();
    });

    verify(gameRunner).host(captor.capture());
    assertThat(captor.getValue().map(), is(map.folderName()));
  }

  @Test
  public void testInitGameTypeComboBoxPostPopulated() {
    FeaturedMod featuredMod = Instancio.of(FeaturedMod.class)
                                       .set(field(FeaturedMod::technicalName), "faf")
                                       .set(field(FeaturedMod::visible), true)
                                       .create();
    when(featuredModService.getFeaturedMods()).thenReturn(Flux.just(featuredMod));

    runOnFxThreadAndWait(() -> reinitialize(instance));

    assertThat(instance.featuredModListView.getItems(), contains(featuredMod));
  }

  @Test
  public void testSelectLastOrDefaultSelectLast() {
    FeaturedMod featuredMod = Instancio.of(FeaturedMod.class)
                                       .set(field(FeaturedMod::technicalName), "last")
                                       .set(field(FeaturedMod::visible), true)
                                       .create();
    FeaturedMod featuredMod2 = Instancio.of(FeaturedMod.class)
                                        .set(field(FeaturedMod::technicalName), "faf")
                                        .set(field(FeaturedMod::visible), true)
                                        .create();

    when(featuredModService.getFeaturedMods()).thenReturn(Flux.just(featuredMod, featuredMod2));

    runOnFxThreadAndWait(() -> {
      reinitialize(instance);
      instance.featuredModListView.getSelectionModel().select(featuredMod);
      reinitialize(instance);
    });

    assertThat(instance.featuredModListView.getSelectionModel().getSelectedItem(), is(featuredMod));
  }

  @Test
  public void testOnlyFriendsBinding() {
    instance.onlyForFriendsCheckBox.setSelected(true);
    assertThat(lastGamePrefs.isLastGameOnlyFriends(), is(true));
    instance.onlyForFriendsCheckBox.setSelected(false);
    assertThat(lastGamePrefs.isLastGameOnlyFriends(), is(false));
  }

  @Test
  public void testPasswordIsSaved() {
    instance.passwordTextField.setText("1234");
    assertEquals(lastGamePrefs.getLastGamePassword(), "1234");
  }

  @Test
  public void testCreateGameTitleTextBorderColor() {
    PseudoClass invalidClass = PseudoClass.getPseudoClass("invalid");
    instance.titleTextField.setText("Test");
    assertThat(instance.titleTextField.getPseudoClassStates().contains(invalidClass), is(false));
    instance.titleTextField.setText("");
    assertThat(instance.titleTextField.getPseudoClassStates().contains(invalidClass), is(true));
  }

  @Test
  public void testOnGenerateMapClicked() {
    when(mapGeneratorService.getNewestGenerator()).thenReturn(Mono.empty());
    when(mapGeneratorService.getGeneratorSymmetries()).thenReturn(Mono.just(List.of()));
    when(mapGeneratorService.getGeneratorStyles()).thenReturn(Mono.just(List.of()));
    when(mapGeneratorService.getGeneratorTerrainStyles()).thenReturn(Mono.just(List.of()));
    when(mapGeneratorService.getGeneratorTextureStyles()).thenReturn(Mono.just(List.of()));
    when(mapGeneratorService.getGeneratorResourceStyles()).thenReturn(Mono.just(List.of()));
    when(mapGeneratorService.getGeneratorPropStyles()).thenReturn(Mono.just(List.of()));

    runOnFxThreadAndWait(() -> instance.onGenerateMapButtonClicked());

    verify(mapGeneratorService).getNewestGenerator();
    verify(mapGeneratorService).getGeneratorSymmetries();
    verify(mapGeneratorService).getGeneratorStyles();
    verify(mapGeneratorService).getGeneratorTerrainStyles();
    verify(mapGeneratorService).getGeneratorTextureStyles();
    verify(mapGeneratorService).getGeneratorResourceStyles();
    verify(mapGeneratorService).getGeneratorPropStyles();
    verify(generateMapController).setSymmetries(any());
    verify(generateMapController).setStyles(any());
    verify(generateMapController).setTerrainStyles(any());
    verify(generateMapController).setTextureStyles(any());
    verify(generateMapController).setResourceStyles(any());
    verify(generateMapController).setPropStyles(any());
    verify(generateMapController).setOnCloseButtonClickedListener(any());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testMapNameSearchFilter() {
    ArgumentCaptor<BiFunction<String, MapVersion, Boolean>> argumentCaptor = ArgumentCaptor.forClass(BiFunction.class);
    verify(mapFilterController).addExternalFilter(any(ObservableValue.class),
                                                  argumentCaptor.capture());
    BiFunction<String, MapVersion, Boolean> filter = argumentCaptor.getValue();

    MapVersion mapVersion = Instancio.of(MapVersion.class)
                                     .set(field(Map::displayName).within(scope(Map.class)), "dual")
                                     .set(field(MapVersion::folderName), "gap.v0001")
                                     .create();
    assertTrue(filter.apply("", mapVersion));
    assertTrue(filter.apply("Gap", mapVersion));
    assertFalse(filter.apply("duel", mapVersion));
    assertTrue(filter.apply("aP", mapVersion));
    assertTrue(filter.apply("Dual", mapVersion));
    assertTrue(filter.apply("ua", mapVersion));
    assertFalse(filter.apply("ap.v1000", mapVersion));
  }

  @Test
  public void testMapNameSearchKeepsSelectedIfInMaps() {
    ArgumentCaptor<BiFunction<String, MapVersion, Boolean>> argumentCaptor = ArgumentCaptor.captor();
    verify(mapFilterController).addExternalFilter(any(ObservableValue.class),
                                                  argumentCaptor.capture());

    BiFunction<String, MapVersion, Boolean> filter = argumentCaptor.getValue();
    ObjectProperty<Predicate<MapVersion>> predicate = mapFilterController.predicateProperty();
    MapVersion map = Instancio.of(MapVersion.class)
                              .set(field(Map::displayName).within(scope(Map.class)), "Test1")
                              .create();
    runOnFxThreadAndWait(() -> {
      mapList.add(map);
      predicate.setValue((item) -> filter.apply("Test", item));
      instance.mapListView.getSelectionModel().select(map);
      instance.mapSearchTextField.setText("Test");
    });

    assertThat(instance.mapListView.getSelectionModel().getSelectedItem(), is(map));

    runOnFxThreadAndWait(() -> {
      predicate.setValue((item) -> filter.apply("Test1", item));
      instance.mapSearchTextField.setText("Test1");
    });

    assertThat(instance.mapListView.getSelectionModel().getSelectedItem(), is(map));
  }

  @Test
  public void testMapNameSearchClearsSelected() {
    ArgumentCaptor<BiFunction<String, MapVersion, Boolean>> argumentCaptor = ArgumentCaptor.captor();
    verify(mapFilterController).addExternalFilter(any(ObservableValue.class),
                                                  argumentCaptor.capture());
    BiFunction<String, MapVersion, Boolean> filter = argumentCaptor.getValue();
    ObjectProperty<Predicate<MapVersion>> predicate = mapFilterController.predicateProperty();
    mapList.add(Instancio.of(MapVersion.class).set(field(Map::displayName).within(scope(Map.class)), "Test1")
                         .create());
                         
    runOnFxThreadAndWait(() -> {
      predicate.setValue((item) -> filter.apply("Not in Filtered Maps", item));
      instance.mapSearchTextField.setText("Not in Filtered Maps");
    });

    assertThat(instance.mapListView.getSelectionModel().getSelectedIndex(), is(-1));
  }
}
