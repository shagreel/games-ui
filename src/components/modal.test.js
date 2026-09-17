import {fireEvent, render, screen} from '@testing-library/react';
import {InteractionModal} from './modal';
import {WebSdkContext} from '../WebSdkContext';

const renderModal = () => {
    render(
        <WebSdkContext.Provider value={jest.fn()}>
            <InteractionModal
                game={{id: 'game-id', name: 'Chess'}}
                onClose={jest.fn()}
            />
        </WebSdkContext.Provider>
    );
};

beforeEach(() => {
    window.localStorage.clear();
});

test('prefills borrower details from local storage', () => {
    window.localStorage.setItem('games-ui.borrower-name', 'Ada Lovelace');
    window.localStorage.setItem('games-ui.borrower-email', 'ada@adobe.com');

    renderModal();

    expect(screen.getByLabelText('Full Name:')).toHaveValue('Ada Lovelace');
    expect(screen.getByLabelText('Email:')).toHaveValue('ada@adobe.com');
});

test('stores borrower details as they are entered', () => {
    renderModal();

    fireEvent.change(screen.getByLabelText('Full Name:'), {
        target: {value: 'Grace Hopper'},
    });
    fireEvent.change(screen.getByLabelText('Email:'), {
        target: {value: 'grace@adobe.com'},
    });

    expect(window.localStorage.getItem('games-ui.borrower-name')).toBe('Grace Hopper');
    expect(window.localStorage.getItem('games-ui.borrower-email')).toBe('grace@adobe.com');
});
