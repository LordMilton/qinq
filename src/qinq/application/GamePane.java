/*
 * Copyright (c) 2016, Andriy Zasypkin.
 *
 * This file is part of Qinq.
 *
 * Qinq(or QINQ) is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Qinq in distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Qinq. If not, see <http://www.gnu.org/licenses/>.
 */

package qinq.application;

import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONObject;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import qinq.resource.Answer;
import qinq.resource.Game;
import qinq.resource.Player;
import qinq.resource.Question;

public class GamePane extends BorderPane {
  private Game       game;
  private Label      labelTime;
  private Label      labelState;
  private HBox       header;
  private FlowPane   players;
  private JSONObject info;

  public GamePane(Game game) {
    this.game = game;
    this.labelState = new Label("Answering");
    this.labelTime = new Label("");
    this.header = new HBox();
    this.players = new FlowPane();
    this.info = new JSONObject();
    this.info.put("action", "info");
    this.info.put("info", "none");

    this.header.getStyleClass().add("header");
    this.players.getStyleClass().add("players");

    this.header.getChildren().addAll(this.labelState, this.labelTime);
    this.setContent(players);

    this.setTop(header);
  }

  public synchronized void refresh() {
    Runnable task = new Runnable() {
      @Override
      public void run() {
        if (GamePane.this.game.getRound() != null
            && GamePane.this.game.getRound().getTime() > 0)
          GamePane.this.labelTime.setText(
              String.format(" - %d", GamePane.this.game.getRound().getTime()));
        else
          GamePane.this.labelTime.setText("");
        if (GamePane.this.labelState.getText().equalsIgnoreCase("Answering")) {
          GamePane.this.players.getChildren().clear();
          GamePane.this.players.getChildren().add(new Label("Waiting on:"));
          for (Player p : GamePane.this.game.getPlayers()) {
            if (p.getAnswers().size() > 0) {
              GamePane.this.players.getChildren().add(p.getLargeLabel());
            }
          }
        }
      }
    };
    if (this.labelState.getText().equalsIgnoreCase("Answering")) {
      info = new JSONObject();
      info.put("action", "info");
      info.put("info", "answering");
      JSONArray jsonPlayers = new JSONArray();
      for (Player p : this.game.getPlayers()) {
        if (p.getAnswers().size() > 0) {
          JSONObject jsonPlayer = new JSONObject();
          jsonPlayer.put("name", p.getName());
          jsonPlayer.put("color", p.getColor());
          jsonPlayers.put(jsonPlayer);
        }
      }
      info.put("players", jsonPlayers);
    }
    if (this.game.getRound() != null)
      info.put("time", this.game.getRound().getTime());
    Platform.runLater(task);
  }

  public synchronized JSONObject getJson() {
    this.refresh();
    return info;
  }

  public synchronized void changeState(String state) {
    if (state.equalsIgnoreCase("Question Results")) {
      Question question = this.game.getRound().getQuestion();
      info = new JSONObject();
      info.put("action", "info");
      info.put("info", "question");
      info.put("question", question.getQuestion());

      question.calcResults(GamePane.this.game.getPlayers().size());

      JSONObject jsonPlayer;
      JSONObject jsonAnswer;
      JSONArray jsonVotes;
      JSONArray jsonAnswers = new JSONArray();
      for (Answer answer : question.getAnswers()) {
        jsonAnswer = new JSONObject();
        jsonPlayer = new JSONObject();
        jsonVotes = new JSONArray();

        jsonPlayer.put("name", answer.getPlayer().getName());
        jsonPlayer.put("color", answer.getPlayer().getColor());

        jsonAnswer.put("player", jsonPlayer);
        jsonAnswer.put("answer", answer.getAnswer());
        jsonAnswer.put("score", answer.getScoreStr());

        int nSpectatorVotes = 0;
        for (Entry<Player, Integer> vote : answer.getVotes().entrySet()) {
          if (vote.getKey().getID() >= 0) {
            jsonPlayer = new JSONObject();
            jsonPlayer.put("value", String.format("%s - %d",
                vote.getKey().getName(), vote.getValue()));
            jsonPlayer.put("color", vote.getKey().getColor());
            jsonVotes.put(jsonPlayer);
          }
          else
            nSpectatorVotes++;
        }
        if (nSpectatorVotes > 0 && game.getSpectators().size() > 0) {
          Player spectator = game.getSpectators().get(0);
          jsonPlayer = new JSONObject();
          jsonPlayer.put("value",
              String.format("%s - %d", spectator.getName(), nSpectatorVotes));
          jsonPlayer.put("color", spectator.getColor());
          jsonVotes.put(jsonPlayer);
        }
        jsonAnswer.put("votes", jsonVotes);
        jsonAnswers.put(jsonAnswer);
      }
      info.put("answers", jsonAnswers);

    }
    else if (state.equalsIgnoreCase("Round Results")) {
      info = new JSONObject();
      info.put("action", "info");
      info.put("info", "round");
      JSONArray jsonPlayers = new JSONArray();
      for (Player p : this.game.getPlayers()) {
        JSONObject player = new JSONObject();
        player.put("name",
            String.format("%s - %d", p.getName(), p.getPoints()));
        player.put("color", p.getColor());
        jsonPlayers.put(player);
      }
      info.put("players", jsonPlayers);

    }
    else if (state.equalsIgnoreCase("Voting")) {
      info = new JSONObject();
      info.put("action", "info");
      info.put("info", "none");
    }
    Platform.runLater(new Runnable() {
      @Override
      public void run() {
        GamePane.this.labelState.setText(state);
        if (state.equalsIgnoreCase("Answering")) {
          GamePane.this.setContent(GamePane.this.players);
        }
        else if (state.equalsIgnoreCase("Question Results")) {
          Question question = GamePane.this.game.getRound().getQuestion();
          GamePane.this.setContent(question.getResultsPane());
        }
        else if (state.equalsIgnoreCase("Round Results")) {
          FlowPane scores = new FlowPane();
          scores.setId("scores");
          for (Player p : GamePane.this.game.getPlayers()) {
            Node scoreNode = p.getNameLabel(String.valueOf(p.getPoints()));
            scores.getChildren().add(scoreNode);
          }
          GamePane.this.setContent(scores);
        }
        else if (state.equalsIgnoreCase("Voting")) {
          GamePane.this.setContent(
              GamePane.this.game.getRound().getQuestion().getVotingPane());
        }
      }
    });
  }

  public synchronized void setContent(Node node) {
    Platform.runLater(new Runnable() {
      @Override
      public void run() {
        GamePane.this.setCenter(node);
      }
    });
  }

}
