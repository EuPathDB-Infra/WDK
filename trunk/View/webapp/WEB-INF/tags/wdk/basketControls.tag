<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="site" tagdir="/WEB-INF/tags/site" %>

<table id="basket-control">
  <tr>
    <td>
      <input id="refresh-basket-button" type="button" value="Refresh" onClick="refreshBasket();"/>
    </td>
    <td>
      <input id="empty-basket-button" type="button" value="Empty basket" onClick="emptyBasket();"/></td>
    <td>
      <input id="make-strategy-from-basket-button" type="button" value="Save basket to a strategy" onClick="saveBasket();"/>
    </td>
    <td>
      <site:customBasketControl />
    </td>
  </tr>
</table>
<div id="basketConfirmation" style="display:none">
  <form action="javascript:void(0);">
    <h3>Are you sure you want to empty the <span id="basketName"></span> basket?</h3>
    <input type="submit" value="Yes" onclick="jQuery.unblockUI();return true;" />
    <input type="submit" value="No" onclick="jQuery.unblockUI();return false;" />
  </form>
</div>
