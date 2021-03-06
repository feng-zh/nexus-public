/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
/*global Ext*/

/**
 * Expire session window.
 *
 * @since 3.0
 */
Ext.define('NX.view.ExpireSession', {
  extend: 'NX.view.ModalDialog',
  requires: [
    'NX.I18n'
  ],
  alias: 'widget.nx-expire-session',

  title: NX.I18n.get('ExpireSession_Title'),

  /**
   * @override
   */
  initComponent: function () {
    var me = this;

    me.setWidth(me.MEDIUM_MODAL);

    Ext.apply(me, {
      items: [
        {
          xtype: 'label',
          id: 'expire',
          text: NX.I18n.get('ExpireSession_Help_Text'),
          style: {
            'color': 'red',
            'font-size': '20px',
            'margin': '12px'
          }
        }
      ],
      buttonAlign: 'left',
      buttons: [
        { text: NX.I18n.get('ExpireSession_Cancel_Button'), action: 'cancel' },
        {
          text: NX.I18n.get('ExpireSession_SignIn_Button'),
          action: 'signin',
          hidden: true,
          itemId: 'expiredSignIn',
          ui: 'nx-primary',
          handler: function() {
            this.up('nx-expire-session').close();
          }
        },
        {
          text: NX.I18n.get('ExpireSession_Close_Button'),
          action: 'close',
          hidden: true,
          handler: function() {
            this.up('nx-expire-session').close();
          }
        }
      ]
    });

    me.callParent();
  },

  /**
   * Check to see if the dialog is showing that it is expired.
   *
   * @public
   * @returns {boolean}
   */
  sessionExpired: function() {
    return this.down('#expiredSignIn').isVisible();
  }

});
