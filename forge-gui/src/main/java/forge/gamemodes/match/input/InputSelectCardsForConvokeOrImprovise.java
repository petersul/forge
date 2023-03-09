package forge.gamemodes.match.input;

import java.util.*;
import java.util.Map.Entry;

import com.google.common.collect.Lists;
import forge.ai.ComputerUtilCard;
import forge.ai.ComputerUtilMana;
import forge.game.card.CardCollection;
import forge.util.Localizer;
import org.apache.commons.lang3.tuple.ImmutablePair;

import forge.card.ColorSet;
import forge.card.mana.ManaCost;
import forge.card.mana.ManaCostShard;
import forge.game.card.Card;
import forge.game.card.CardCollectionView;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.localinstance.properties.ForgePreferences;
import forge.model.FModel;
import forge.player.PlayerControllerHuman;
import forge.util.ITriggerEvent;
import forge.util.TextUtil;


public final class InputSelectCardsForConvokeOrImprovise extends InputSelectManyBase<Card> {
    private static final long serialVersionUID = -1779224307654698954L;
    private final Map<Card, ImmutablePair<Byte, ManaCostShard>> chosenCards = new HashMap<>();
    private final ManaCostBeingPaid remainingCost;
    private final Player player;
    private final CardCollectionView availableCards;
    private final boolean improvise;
    private final String cardType;
    private final String description;
    private SpellAbility sa;

    public InputSelectCardsForConvokeOrImprovise(final PlayerControllerHuman controller, final Player p, final ManaCost cost, final CardCollectionView untapped, boolean impr, final SpellAbility sa) {
        super(controller, 0, Math.min(cost.getCMC(), untapped.size()), sa);
        remainingCost = new ManaCostBeingPaid(cost);
        player = p;
        availableCards = untapped;
        improvise = impr;
        cardType = impr ? "artifact" : "creature";
        description = impr ? "Improvise" : "Convoke";
    }

    @Override
    public void updateButtons() {
        Localizer localizer = Localizer.getInstance();
        getController().getGui().updateButtons(getOwner(), localizer.getMessage("lblOk"), localizer.getMessage("lblAuto"), true, true, true);
    }

    @Override
    protected String getMessage() {
        StringBuilder sb = new StringBuilder();
        if (FModel.getPreferences().getPrefBoolean(ForgePreferences.FPref.UI_DETAILED_SPELLDESC_IN_PROMPT) &&
                sa != null) {
            sb.append(sa.getStackDescription()).append("\n");
        }
        sb.append(TextUtil.concatNoSpace("Choose ", cardType, " to tap for ", description, ".\nRemaining mana cost is ", remainingCost.toString()));
        return sb.toString();
    }

    @Override
    protected boolean onCardSelected(final Card card, final List<Card> otherCardsToSelect, final ITriggerEvent triggerEvent) {
        if (!availableCards.contains(card)) {
            // Not in untapped list provided. Not a legal Convoke selection.
            return false;
        }

        final boolean entityWasSelected = chosenCards.containsKey(card);
        if (entityWasSelected) {
            final ImmutablePair<Byte, ManaCostShard> color = this.chosenCards.remove(card);
            remainingCost.increaseShard(color.right, 1);
            onSelectStateChanged(card, false);
        }
        else {
            byte chosenColor;
            if (improvise) {
                chosenColor = ManaCostShard.COLORLESS.getColorMask();
            } else {
                ColorSet colors = card.getColor();
                if (colors.isMulticolor()) {
                    //if card is multicolor, strip out any colors which can't be paid towards remaining cost
                    colors = ColorSet.fromMask(colors.getColor() & remainingCost.getUnpaidColors());
                }
                if (!colors.isMulticolor()) {
                    // Since the convoke mana logic can use colored mana as generic if needed,
                    // there is no need to prompt the user when convoking with a mono-color creature.
                    chosenColor = colors.getColor();
                } else { //prompt user if more than one option for which color to pay towards convoke
                    chosenColor = player.getController().chooseColorAllowColorless("Convoke " + card.toString() + "  for which color?", card, colors);
                }
            }
            final ManaCostShard shard = remainingCost.payManaViaConvoke(chosenColor);
            if (shard != null) {
                chosenCards.put(card, ImmutablePair.of(chosenColor, shard));
                onSelectStateChanged(card, true);
            }
            else {
                showMessage("The colors provided by " + card.toString() + " you've chosen cannot be used to pay the manacost of " + remainingCost.toString());
                return false;
            }
        }

        refresh();
        return true;
    }

    @Override
    public String getActivateAction(final Card card) {
        if (availableCards.contains(card)) {
            return TextUtil.concatNoSpace("tap ", cardType, " for ", description);
        }
        return null;
    }

    @Override
    protected final void onPlayerSelected(final Player player, final ITriggerEvent triggerEvent) {
    }

    public Map<Card, ManaCostShard> getConvokeMap() {
        final Map<Card, ManaCostShard> result = new HashMap<>();
        if(!hasCancelled()) {
            for(final Entry<Card, ImmutablePair<Byte, ManaCostShard>> c : chosenCards.entrySet()) {
                result.put(c.getKey(), c.getValue().right);
            }
        }
        return result;
    }

    @Override
    protected boolean hasEnoughTargets() { return true; }

    @Override
    protected boolean hasAllTargets() { return false; }

    @Override
    public Collection<Card> getSelected() {
        return chosenCards.keySet();
    }

    @Override
    protected void onCancel() {
        // Auto choose a bunch of things to select
        CardCollection available = (CardCollection) availableCards;

        if (improvise) {
            // SOrt by worst artifact. Which we don't have a great way of doing right now
        } else {
            ComputerUtilCard.sortByEvaluateCreature(available);
            Lists.reverse(available);
        }

        Set<Card> convoked = ComputerUtilMana.getConvokeOrImproviseFromList(remainingCost.toManaCost(), available, improvise).keySet();

        for(Card card : convoked) {
            onCardSelected(card, null, null);
        }

        refresh();

        //this.onOk();
    }
}
